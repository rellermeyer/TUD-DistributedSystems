package dynamodb.node

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.http.scaladsl.Http.ServerBinding
import akka.util.Timeout
import dynamodb.node.DistributedHashTable.{AddNode, Response}
import dynamodb.node.ring.RingNode
import dynamodb.node.mainObj.NodeConfig

import scala.concurrent.duration._

object Node {

  sealed trait Message

  private final case class StartFailed(cause: Throwable) extends Message

  private final case class Started(binding: ServerBinding) extends Message

  private final case class StartFailedInternal(cause: Throwable) extends Message

  private final case class StartedInternal(binding: ServerBinding) extends Message
  private final case class StopInternal(binding: ServerBinding) extends Message
  private final case class StopExternal(binding: ServerBinding) extends Message

  case object Stop extends Message

  def apply(config: NodeConfig, allNodes: List[NodeConfig], clusterConfig: ClusterConfig): Behavior[Message] = Behaviors.setup { ctx =>

    implicit val scheduler: Scheduler = ctx.system.scheduler
    implicit val timeout: Timeout = Timeout(3.seconds)

    ctx.log.info("Starting node {}", config.name)
    implicit val dht: ActorRef[DistributedHashTable.Command] = ctx.spawn(DistributedHashTable(), "DistributedHashTable")

    for (node <- allNodes) {
      dht ! AddNode(RingNode(node.position, node.internalHost, node.internalPort, node.externalHost, node.externalPort, node.name), ctx.system.ignoreRef[Response])
    }

    implicit val buildValueRepository: ActorRef[ValueRepository.Command] = ctx.spawn(ValueRepository(config.name), "ValueRepository")

    val internalClient = ctx.spawn(InternalClient(config.internalHost, config.internalPort, clusterConfig.numReplicas, clusterConfig.numReadMinimum, clusterConfig.numWriteMinimum, config.name), "InternalClient")

    val internalServer = ctx.spawn(InternalServer(buildValueRepository, config.internalHost, config.internalPort, config.name), "InternalServer")
    val externalServer = ctx.spawn(ExternalServer(buildValueRepository, internalClient, config.externalHost, config.externalPort), "ExternalServer")

    def starting(): Behaviors.Receive[Message] =
      Behaviors.receiveMessagePartial[Message] {
        case Stop =>
          internalServer ! InternalServer.Stop()
          externalServer ! ExternalServer.Stop()
          Behaviors.same
      }

    starting()
  }

}
