package sample.cluster.byzantine

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import sample.cluster.CborSerializable

import scala.::

object SyncNode {
  val SyncNodeServiceKey: ServiceKey[SyncNode.Event] = ServiceKey[SyncNode.Event]("Nodes")

  private final val C = 2
  private final val EPISOLON = 0.01
  private final val T = 1

  sealed trait Event
  private final case class NodesUpdated(newWorkers: Set[ActorRef[SyncNode.Event]]) extends Event
  private final case class LargeCoBeRaUpdated(largeCoBeRa: Set[ActorRef[LargeCoBeRa.AgreementSelection]]) extends Event

  sealed trait Request extends Event with CborSerializable
  case class Save(nodeId: Int, replyTo: ActorRef[SyncNode.Event]) extends Request
  case class RegisterMyID(messageId: Int, nodeId: Int, reply: ActorRef[SyncNode.Event]) extends Request

  sealed trait Response extends Event with CborSerializable
  case class Ack(messageId: Int) extends Response
  case class QueryReply(messageId: Int) extends Response


  def apply(nodeId: Int, numberOfNodes: Int): Behavior[SyncNode.Event] = Behaviors.setup { ctx =>
    val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
      case SyncNode.SyncNodeServiceKey.Listing(workers) =>
        NodesUpdated(workers)
      case LargeCoBeRa.CoBeRaServiceKey.Listing(largeCoBeRa) =>
        LargeCoBeRaUpdated(largeCoBeRa)
    }

    ctx.system.receptionist ! Receptionist.Subscribe(SyncNode.SyncNodeServiceKey, subscriptionAdapter)

    // each worker registers themselves with the receptionist
    ctx.log.info("Registering new node")
    ctx.system.receptionist ! Receptionist.Register(SyncNodeServiceKey, ctx.self)

    var nodesReferences = IndexedSeq.empty[ActorRef[SyncNode.Event]]
    var activeNodes: Map[Int, ActorRef[SyncNode.Event]] = Map.empty

    // define the state of the actor
    val r = scala.util.Random
    val p: Double = (C * math.log(numberOfNodes))/numberOfNodes
    val low: Double = numberOfNodes - 2 * T - EPISOLON * numberOfNodes
    val high: Double = low + T
    val minA: Double = (1 - EPISOLON) * p * (numberOfNodes - T)
    val maxA: Double = (1 + EPISOLON) * p * (numberOfNodes - T)
    val beta: Double = (1 - EPISOLON) * (low - T) / (maxA + EPISOLON * p * numberOfNodes)
    val delta: Double = (1 - EPISOLON) * (low - T) / numberOfNodes
    var readyOut: Int = 0
    var readyIn: Int = 0
    val active: Boolean = r.nextDouble() <= p
    var light: Boolean = false

    // synchronization
    var roundID: Int = 2
    var outstandingMessageIDs: List[Int] = List.empty
    var seqNum: Int = 0
//    var saveNodes: Set[ActorRef[SyncNode.Event]] = Set.empty
    var saveNodes: Set[Int] = Set.empty

    def resetSaveNodes(): Unit = saveNodes = Set.empty
    def broadCastSaves(): Unit = nodesReferences.foreach(ref => {
      println(s"I am $nodeId and I am broadcasting saves to $ref")
      ref ! Save(nodeId, ctx.self)
    })

    def receivedMessageId(seqNum: Int): Unit = outstandingMessageIDs = outstandingMessageIDs.filter(_ != seqNum)

    ctx.log.info(s"I am node: $nodeId and im ${if (active) "active" else "not active"}")
    Behaviors.withTimers { timers =>
      Behaviors.receiveMessage {
        case NodesUpdated(newWorkers) =>
          ctx.log.info("List of services registered with the receptionist changed: {}", newWorkers)
          nodesReferences = newWorkers.toIndexedSeq
          if (nodesReferences.size == numberOfNodes && active) {
            nodesReferences.foreach(ref => {
              seqNum = seqNum + 1
              println(s"sending message with seqnum $nodeId-$seqNum")
              outstandingMessageIDs = outstandingMessageIDs :+ seqNum
              ref ! RegisterMyID(seqNum, nodeId, ctx.self)
            })
          }
          else if (nodesReferences.size == numberOfNodes) {
            broadCastSaves()
          }
          Behaviors.same
        case RegisterMyID(seqNum, senderId, senderAddress) =>
          println(s"registering an active id: $senderId")
          activeNodes = activeNodes + (senderId -> senderAddress)
          senderAddress ! Ack(seqNum)
          Behaviors.same
        case Ack(seqNum) =>
          println(s"received ack for seqnum $seqNum")
          println(s"current outstanding message $outstandingMessageIDs")
          receivedMessageId(seqNum)
          println(s"new outstanding messageids $outstandingMessageIDs")
          if (outstandingMessageIDs.size == 0 || outstandingMessageIDs.isEmpty)
            broadCastSaves()
          Behaviors.same
        case Save(senderId, senderAddress) =>
          saveNodes += senderId
          println(s"I am node $nodeId savenodes: $saveNodes")
          if (saveNodes.size == numberOfNodes)
            println(s"---------- DONE WITH ROUND $roundID ----------")
          Behaviors.same
      }
    }
  }

}
