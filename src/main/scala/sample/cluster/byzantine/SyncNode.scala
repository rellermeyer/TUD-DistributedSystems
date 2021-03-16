package sample.cluster.byzantine

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import sample.cluster.CborSerializable

import scala.::

object SyncNode {
  val SyncNodeServiceKey: ServiceKey[SyncNode.Event] = ServiceKey[SyncNode.Event]("Nodes")

  private final val C = 2
  private final val EPISOLON = 0.1
  private final val T = 1

  sealed trait Event
  private final case class NodesUpdated(newWorkers: Set[ActorRef[SyncNode.Event]]) extends Event
  private final case class LargeCoBeRaUpdated(largeCoBeRa: Set[ActorRef[LargeCoBeRa.AgreementSelection]]) extends Event

  sealed trait Request extends Event with CborSerializable
  case class Save(nodeId: Int, replyTo: ActorRef[SyncNode.Event]) extends Request
  case class RegisterMyID(messageId: Int, nodeId: Int, reply: ActorRef[SyncNode.Event]) extends Request
  case class SelectedActiveID(messageId: Int, selectedId: Int, selectedAddress: ActorRef[SyncNode.Event], reply: ActorRef[SyncNode.Event]) extends Request
  case class Query(messageId: Int, queryId: Int, queryAddress: ActorRef[SyncNode.Event], senderId: Int, reply: ActorRef[SyncNode.Event]) extends Request

  sealed trait Response extends Event with CborSerializable
  case class Ack(messageId: Int) extends Response
  case class QueryReply(messageId: Int, queryId: Int, reply: ActorRef[SyncNode.Event]) extends Response


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
    var selectedNodes: List[(Int, ActorRef[SyncNode.Event])] = List.empty
    // A list with queryID, queryAddress, senderID, senderAddress
    var queryList: List[(Int, ActorRef[SyncNode.Event], Int, ActorRef[SyncNode.Event])] = List.empty
    var queryReplies: List[Int] = List.empty

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
    def broadCastSaves(): Unit = nodesReferences.foreach(ref => ref ! Save(nodeId, ctx.self))

    def receivedMessageId(seqNum: Int): Unit = outstandingMessageIDs = outstandingMessageIDs.filter(_ != seqNum)

    Behaviors.withTimers { timers =>
      Behaviors.receiveMessage {
        case NodesUpdated(newWorkers) =>
          ctx.log.info("List of services registered with the receptionist changed: {}", newWorkers)
          nodesReferences = newWorkers.toIndexedSeq
          if (nodesReferences.size == numberOfNodes && active) {
            nodesReferences.foreach(ref => {
              seqNum = seqNum + 1
              outstandingMessageIDs = outstandingMessageIDs :+ seqNum
              ref ! RegisterMyID(seqNum, nodeId, ctx.self)
            })
          }
          else if (nodesReferences.size == numberOfNodes)
            broadCastSaves()
          Behaviors.same
        case RegisterMyID(seqNum, senderId, senderAddress) =>
          activeNodes = activeNodes + (senderId -> senderAddress)
          light = activeNodes.size < (maxA + EPISOLON * p * numberOfNodes)
          senderAddress ! Ack(seqNum)
          Behaviors.same
        case SelectedActiveID(seqNum, selectedID, selectedAddress, senderAddress) =>
          println(s"registering an selected ID: $selectedID")
          selectedNodes = selectedNodes :+ (selectedID, selectedAddress)
          senderAddress ! Ack(seqNum)
          Behaviors.same
        case Query(seqNum, queryID, queryAddress, senderId, senderAddress) =>
          queryList = queryList :+ (queryID, queryAddress, senderId, senderAddress)
          senderAddress ! Ack(seqNum)
          Behaviors.same
        case QueryReply(seqNum, queryId, senderAddress) =>
          queryReplies = queryReplies :+ queryId
          senderAddress ! Ack(seqNum)
          Behaviors.same
        case Ack(seqNum) =>
          receivedMessageId(seqNum)
          println(s"new outstanding messageids $outstandingMessageIDs")
          if (outstandingMessageIDs.isEmpty)
            broadCastSaves()
          Behaviors.same
        case Save(senderId, senderAddress) =>
          saveNodes += senderId
          println(s"saved nodes $saveNodes: ${saveNodes.size}/$numberOfNodes")
          if (saveNodes.size == numberOfNodes) {
            println(s"---------- DONE WITH ROUND $nodeId:$roundID ----------")
            resetSaveNodes()
            roundID = roundID + 1
            roundID match {
              case 3 =>
                println(s"I am node $nodeId and I am light: $light")
                if (light) {
                  val (selectedId, selectedAddress) = activeNodes.toList(r.nextInt(activeNodes.size))
                  activeNodes.foreach(entry => {
                    seqNum = seqNum + 1
                    println(s"sending selected message with seqnum $nodeId-$seqNum")
                    outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                    entry._2 ! SelectedActiveID(seqNum, selectedId, selectedAddress, ctx.self)
                  })
                }
                else
                  broadCastSaves()
              case 4 =>
                println(s"I am node $nodeId and I am active: $active")
                if (active) {
                  val n: Int = selectedNodes.size
                  if (n > low - T) {
                    activeNodes = selectedNodes
                      .groupBy(tuple => tuple._1)
                      .filter(entry => entry._2.size >= beta)
                      .values
                      .flatten
                      .toMap

                    activeNodes.foreach(entry => {
                      r
                        .shuffle(nodesReferences)
                        .take(math.round(C * math.log(numberOfNodes)).toInt)
                        .foreach(ref => {
                          seqNum = seqNum + 1
                          println(s"sending query message with seqnum $nodeId-$seqNum towards ${entry._1}")
                          outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                          ref ! Query(seqNum, entry._1, entry._2, nodeId, ctx.self)
                        })
                    })
                  }
                }
                else
                  broadCastSaves()
              case 5 =>
                if (light) {
                  queryList
                    .filter(entry => activeNodes.contains(entry._1) && activeNodes.contains(entry._3))
                    .foreach(entry => {
                      seqNum = seqNum + 1
                      println(s"sending query reply message with seqnum $nodeId-$seqNum")
                      outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                      entry._4 ! QueryReply(seqNum, entry._1, ctx.self)
                    })
                }
                else
                  broadCastSaves()
              case 6 =>
                val validIDs: Set[Int] = queryReplies
                  .groupBy(entry => entry)
                  .filter(entry => entry._2.size >= delta * C * math.log(numberOfNodes))
                  .keySet

                activeNodes = activeNodes.view.filterKeys(key => !validIDs.contains(key)).toMap
                if (active) {
                  val n: Int = selectedNodes.size
                  if (n > low - T) {
                    println("perform large cobera")
                  }
                }
                else
                  broadCastSaves()
              case _ => println("I have not been implemented")
            }
          }
          Behaviors.same
      }
    }
  }

}
