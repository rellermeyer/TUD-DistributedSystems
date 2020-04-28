package nl.tudelft.htable.server

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector, PostStop}
import nl.tudelft.htable.core.Node
import nl.tudelft.htable.protocol.CoreAdapters
import nl.tudelft.htable.server.curator.{GroupMember, GroupMemberListener}
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.imps.CuratorFrameworkState
import org.apache.curator.framework.recipes.cache.ChildData
import org.apache.curator.framework.recipes.leader.{LeaderLatch, LeaderLatchListener}
import org.apache.curator.framework.recipes.nodes.PersistentNode
import org.apache.curator.framework.state.ConnectionState
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode

import scala.language.implicitConversions

/**
 * An actor for managing the ZooKeeper connection.
 */
object ZooKeeperActor {

  /**
   * Commands that are accepted by the [ZooKeeperManager].
   */
  sealed trait Command

  /**
   * Events emitted by the [ZooKeeperManager].
   */
  sealed trait Event

  /**
   * Internal message sent when a new self has joined the cluster.
   */
  final case class NodeJoined(node: Node) extends Event

  /**
   * Internal message sent when a self has left the cluster.
   */
  final case class NodeLeft(node: Node) extends Event

  /**
   * Message to update the location of the root tablet.
   */
  final case class SetRoot(node: Option[Node]) extends Command

  /**
   * Internal message indicating that the server was elected to be the leader.
   */
  final case object Elected extends Event

  /**
   * Internal message indicating that the server was overthrown.
   */
  final case object Overthrown extends Event

  /**
   * Internal message indicating ZooKeeper connection was successful.
   */
  private final case object Connected extends Command

  /**
   * Internal message indicating ZooKeeper disconnected.
   */
  private final case object Disconnected extends Command

  /**
   * Construct the behavior for the ZooKeeper manager.
   *
   * @param zookeeper The ZooKeeper client to use.
   * @param self The self to open the connection for.
   * @param listener  The listener to emit events to.
   */
  def apply(self: Node, zookeeper: CuratorFramework, listener: ActorRef[Event]): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Connecting to ZooKeeper")

      zookeeper.start()
      zookeeper.getConnectionStateListenable.addListener((_: CuratorFramework, newState: ConnectionState) =>
        if (newState.isConnected) {
          context.self ! Connected
        } else {
          context.self ! Disconnected
      })

      Behaviors
        .receiveMessage[Command] {
          case Connected    => connected(self, zookeeper, listener)
          case Disconnected => Behaviors.stopped
          case _            => throw new IllegalStateException()
        }
        .receiveSignal {
          case (_, PostStop) =>
            context.log.debug("Stopping ZooKeeper client")
            zookeeper.close()
            Behaviors.same
        }
    }

  /**
   * Construct the behavior for when the ZooKeeper client is connected.
   */
  private def connected(self: Node, zookeeper: CuratorFramework, listener: ActorRef[Event]): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("Joining leader election")

      // Create group membership based on Curator Recipes
      val membership =
        new GroupMember(zookeeper, "/servers", self.uid, CoreAdapters.serializeAddress(self.address))
      membership.addListener(new GroupMemberListener {
        override def memberJoined(data: ChildData): Unit = listener ! NodeJoined(data)
        override def memberLeft(data: ChildData): Unit = listener ! NodeLeft(data)
      })
      membership.start()

      // Perform leader election via Curator Recipes
      val leaderLatch = new LeaderLatch(zookeeper, "/leader", self.uid)
      leaderLatch.addListener(
        new LeaderLatchListener {
          override def isLeader(): Unit = listener ! Elected
          override def notLeader(): Unit = listener ! Overthrown
        },
        context.system.dispatchers.lookup(DispatcherSelector.blocking())
      )
      leaderLatch.start()

      // If we are hosting the root METADATA tablet, we claim the root node within ZooKeeper
      var rootClaim: Option[PersistentNode] = None

      Behaviors
        .receiveMessage[Command] {
          case SetRoot(node) =>
            node match {
              case Some(node) =>
                context.log.debug(s"Root tablet located at $node")
                rootClaim match {
                  case Some(pen) =>
                    pen.setData(node.uid.getBytes("UTF-8"))
                  case None =>
                    val pen =
                      new PersistentNode(zookeeper, CreateMode.EPHEMERAL, false, "/root", node.uid.getBytes("UTF-8"))
                    pen.start()
                    rootClaim = Some(pen)
                }
              case None =>
                context.log.debug("Unclaiming root tablet")
                rootClaim.foreach { pen =>
                  pen.close()
                  rootClaim = None
                }
            }
            Behaviors.same
          case Disconnected => throw new IllegalStateException("ZooKeeper has disconnected")
          case _            => throw new IllegalStateException()
        }
        .receiveSignal {
          case (_, PostStop) =>
            context.log.debug("Stopping ZooKeeper client")
            leaderLatch.close()
            membership.close()
            rootClaim.foreach(_.close())
            zookeeper.close()
            Behaviors.same
        }
    }

  /**
   * Convert [ChildData] into [Node].
   */
  private implicit def toNode(data: ChildData): Node =
    Node(ZKPaths.getNodeFromPath(data.getPath), CoreAdapters.deserializeAddress(data.getData))
}
