package nl.tudelft.htable.server

import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import nl.tudelft.htable.client.impl.DefaultServiceResolverImpl
import nl.tudelft.htable.client.{CachingServiceResolver, HTableClient, HTableInternalClient}
import nl.tudelft.htable.core._
import nl.tudelft.htable.server.services.{AdminServiceImpl, ClientServiceImpl, InternalServiceImpl}
import nl.tudelft.htable.server.util.ServerServiceResolver
import nl.tudelft.htable.storage.StorageDriverProvider
import org.apache.curator.framework.CuratorFramework

import scala.collection.mutable
import scala.concurrent.ExecutionContext

object HTableActor {

  /**
   * Internal commands that are accepted by the [HTableServer].
   */
  sealed trait Command

  /**
   * Internal message wrapper for Node event.
   */
  private final case class NodeEvent(event: NodeActor.Event) extends Command

  /**
   * Internal message wrapper for Admin event.
   */
  private final case class AdminEvent(event: AdminActor.Event) extends Command

  /**
   * Internal message wrapper for gRPC event.
   */
  private final case class GRPCEvent(event: GRPCActor.Event) extends Command

  /**
   * Internal message wrapper for ZooKeeper event.
   */
  private final case class ZooKeeperEvent(event: ZooKeeperActor.Event) extends Command

  /**
   * Construct the main logic of the server.
   *
   * @param self The node to represent.
   * @param zk The ZooKeeper client.
   * @param sdp The storage driver to use.
   * @param loadBalancerPolicy The load balancer policy to use.
   */
  def apply(self: Node,
            zk: CuratorFramework,
            sdp: StorageDriverProvider,
            loadBalancerPolicy: LoadBalancerPolicy): Behavior[Command] =
    Behaviors
      .setup[Command] { context =>
        implicit val sys: ActorSystem[Nothing] = context.system
        context.log.info("Booting HTable server")

        // Spawn the node actor
        val nodeAdapter = context.messageAdapter(HTableActor.NodeEvent)
        val nodeActor = context.spawn(NodeActor(self, sdp, nodeAdapter), name = "node")
        // Kill ourselves if the child dies
        context.watch(nodeActor)

        // Spawn the admin actor
        val adminAdapter = context.messageAdapter(HTableActor.AdminEvent)
        val admin = context.spawn(AdminActor(adminAdapter), name = "admin")
        context.watch(admin)

        // Create the client for communication with other nodes
        val clientService = new ClientServiceImpl(nodeActor)
        val adminService = new AdminServiceImpl(admin)
        val internalService = new InternalServiceImpl(nodeActor)
        val client = HTableClient.createInternal(
          zk,
          context.system.toClassic,
          new ServerServiceResolver(
            self,
            new CachingServiceResolver(new DefaultServiceResolverImpl(context.system.toClassic)),
            clientService,
            adminService,
            internalService)
        )

        // Spawn the gRPC services actor
        val grpcAdapter = context.messageAdapter(HTableActor.GRPCEvent)
        val grpc =
          context.spawn(GRPCActor(self.address, clientService, adminService, internalService, grpcAdapter),
                        name = "grpc-server")
        context.watch(grpc)

        // Wait for the gRPC server to be ready
        Behaviors.receiveMessagePartial {
          case GRPCEvent(GRPCActor.ServiceActive) =>
            // Spawn the ZooKeeper actor
            val zkAdapter = context.messageAdapter(HTableActor.ZooKeeperEvent)
            val zkRef = context.spawn(ZooKeeperActor(self, zk, zkAdapter), name = "zookeeper")
            context.watch(zkRef)

            // Enable the node now (by default)
            nodeActor ! NodeActor.Enable(client)

            // Spawn the load balancer
            val loadBalancer =
              context.spawn(LoadBalancerActor(client, loadBalancerPolicy), name = "load-balancer")
            context.watch(loadBalancer)

            started(self, client, admin, loadBalancer, zkRef)
        }
      }

  /**
   * Construct the behavior of the server when it has started.
   *
   * @param self The self that has been spawned.
   * @param client The client to communicate with other nodes.
   * @param admin The admin actor.
   * @param loadBalancer The reference to the load balancer.
   * @param zk The reference to the ZooKeeper actor.
   * @param isMaster A flag to indicate the node is a master.
   * @param nodes The active nodes in the cluster.
   */
  def started(self: Node,
              client: HTableInternalClient,
              admin: ActorRef[AdminActor.Command],
              loadBalancer: ActorRef[LoadBalancerActor.Command],
              zk: ActorRef[ZooKeeperActor.Command],
              isMaster: Boolean = false,
              nodes: mutable.Set[Node] = mutable.HashSet()): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.default())
      Behaviors
        .receiveMessage[Command] {
          case ZooKeeperEvent(ZooKeeperActor.Elected) =>
            context.log.info("Node has been elected")
            // Enable admin endpoint
            admin ! AdminActor.Enable(client)

            // Schedule a load balancing job
            loadBalancer ! LoadBalancerActor.Schedule(nodes.toSet)

            started(self, client, admin, loadBalancer, zk, isMaster = true, nodes)
          case ZooKeeperEvent(ZooKeeperActor.Overthrown) =>
            context.log.info("Node has been overthrown")
            Behaviors.stopped
          case ZooKeeperEvent(ZooKeeperActor.NodeJoined(node)) =>
            context.log.info(s"Node ${node.uid} has joined")
            nodes += node

            if (isMaster) {
              // Start a load balancing cycle
              loadBalancer ! LoadBalancerActor.Schedule(nodes.toSet)
            }

            Behaviors.same
          case ZooKeeperEvent(ZooKeeperActor.NodeLeft(node)) =>
            context.log.info(s"Node ${node.uid} has left")
            nodes -= node

            if (isMaster) {
              // Start a load balancing cycle
              loadBalancer ! LoadBalancerActor.Schedule(nodes.toSet)
            }

            Behaviors.same
          case AdminEvent(AdminActor.Balanced(_, shouldInvalidate)) =>
            context.log.info(s"Rebalancing tablets [invalidate=$shouldInvalidate]")
            assert(isMaster, "Non-masters cannot rebalance")
            // Start a load balancing cycle
            loadBalancer ! LoadBalancerActor.Schedule(nodes.toSet, shouldInvalidate)
            Behaviors.same
          case NodeEvent(NodeActor.Serving(tabletsAdded, tabletsRemoved)) =>
            context.log.debug(s"$self has been assigned new tablets [added=$tabletsAdded, removed=$tabletsRemoved]")
            if (tabletsAdded.exists(Tablet.isRoot)) {
              zk ! ZooKeeperActor.SetRoot(Some(self))
            } else if (tabletsRemoved.exists(Tablet.isRoot)) {
              zk ! ZooKeeperActor.SetRoot(None)
            }
            Behaviors.same
          case _ => throw new IllegalArgumentException()
        }
    }
}
