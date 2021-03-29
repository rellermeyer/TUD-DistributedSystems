package sample.cluster.byzantine

import akka.actor.TypedActor.self
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import sample.cluster.CborSerializable

import scala.{:+, ::}
import scala.collection.mutable

object SyncNode {
  val SyncNodeServiceKey: ServiceKey[SyncNode.Event] = ServiceKey[SyncNode.Event]("SyncNodes")

  private final val C = 2
  final val EPISOLON = 0.1
  final val T = 1

  sealed trait Event
  private final case class NodesUpdated(newWorkers: Set[ActorRef[SyncNode.Event]]) extends Event
  private final case class LargeCoBeRaUpdated(largeCoBeRa: Set[ActorRef[LargeCoBeRa.Command]]) extends Event
  case object TimerKey extends Event

  sealed trait Request extends Event with CborSerializable
  case class Save(nodeId: Int, roundId: Int, replyTo: ActorRef[SyncNode.Event]) extends Request
  case class RegisterMyID(messageId: Int, nodeId: Int, reply: ActorRef[SyncNode.Event]) extends Request
  case class SelectedActiveID(messageId: Int, selectedId: Int, selectedAddress: ActorRef[SyncNode.Event], reply: ActorRef[SyncNode.Event]) extends Request
  case class Query(messageId: Int, queryId: Int, queryAddress: ActorRef[SyncNode.Event], senderId: Int, reply: ActorRef[SyncNode.Event]) extends Request
  case class BroadcastCoreBAResult(seqNum: Int, readyOut: Int, valueOut: Int, senderID: Int, reply: ActorRef[SyncNode.Event]) extends Request
  case class PromiseRequest(seqNum: Int, replyTo: ActorRef[SyncNode.Event]) extends Request

  sealed trait Response extends Event with CborSerializable
  case class Ack(messageId: Int) extends Response
  case class QueryReply(messageId: Int, queryId: Int, reply: ActorRef[SyncNode.Event]) extends Response
  case class LargeCoreBaResponse(result: Int, resultValue: Int) extends Response
  case class LargeCoreBaFailure(throwable: Throwable) extends Response
  case class PromiseResponse(messageId: Int, readyOut: Int, valueOut: Int) extends Response


  def apply(nodeId: Int, numberOfNodes: Int): Behavior[SyncNode.Event] = Behaviors.setup { ctx =>
    val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
      case SyncNode.SyncNodeServiceKey.Listing(workers) =>
        NodesUpdated(workers)
      case LargeCoBeRa.CoBeRaServiceKey.Listing(largeCoBeRa) =>
        LargeCoBeRaUpdated(largeCoBeRa)
    }

    ctx.system.receptionist ! Receptionist.Subscribe(SyncNode.SyncNodeServiceKey, subscriptionAdapter)
    ctx.system.receptionist ! Receptionist.Subscribe(LargeCoBeRa.CoBeRaServiceKey, subscriptionAdapter)

    // each worker registers themselves with the receptionist
    ctx.log.info("Registering new node")
    ctx.system.receptionist ! Receptionist.Register(SyncNodeServiceKey, ctx.self)


    var nodesReferences = IndexedSeq.empty[ActorRef[SyncNode.Event]]
    var activeNodes: Map[Int, ActorRef[SyncNode.Event]] = Map.empty
    var selectedNodes: List[(Int, ActorRef[SyncNode.Event])] = List.empty
    var largeCoreBa = IndexedSeq.empty[ActorRef[LargeCoBeRa.Command]]

    // A list with queryID, queryAddress, senderID, senderAddress
    var queryList: List[(Int, ActorRef[SyncNode.Event], Int, ActorRef[SyncNode.Event])] = List.empty
    var queryReplies: List[Int] = List.empty

    // coreba results
    // nodeID -> (readyOut, valueOut)
    var coreBAResult = Map.empty[Int, (Int, Int)]

    // promise response [(readyOut, valueOut)]
    var promiseResponses = List.empty[(Int, Int)]

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
    var value: Int = 0
    val active: Boolean = r.nextDouble() <= p
    var light: Boolean = false

    // synchronization
    var roundID: Int = 2
    var outstandingMessageIDs: List[Int] = List.empty
    var seqNum: Int = 0
    var saveNodes: Set[Int] = Set.empty

    // savemap -> roundID -> set[nodeIDs]
    var saveMap: mutable.Map[Int, Set[Int]] = mutable.Map.empty[Int, Set[Int]]

    def resetSaveNodes(roundID: Int): Unit = saveMap(roundID) = Set.empty
    def broadCastSaves(): Unit = nodesReferences.foreach(ref => ref ! Save(nodeId, roundID, ctx.self))

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
        case LargeCoBeRaUpdated(newCoreBAs) =>
          ctx.log.info("List of corebas registered with the receptionist changed: {}", newCoreBAs)
          largeCoreBa = newCoreBAs.toIndexedSeq
          if (active && largeCoreBa.nonEmpty)
            largeCoreBa(0) ! LargeCoBeRa.RegisterActiveNode(nodeId)
          Behaviors.same
        case RegisterMyID(seqNum, senderId, senderAddress) =>
          activeNodes = activeNodes + (senderId -> senderAddress)
          light = activeNodes.size < (maxA + EPISOLON * p * numberOfNodes)
          senderAddress ! Ack(seqNum)
          Behaviors.same
        case SelectedActiveID(seqNum, selectedID, selectedAddress, senderAddress) =>
//          println(s"registering an selected ID: $selectedID")
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
        case LargeCoreBaResponse(readyOutCoreBA, valueCoreBA) =>
          broadCastSaves()
          if (selectedNodes.size >= low && readyOutCoreBA != -1) readyOut = readyOutCoreBA
          else if (valueCoreBA != -1) value = valueCoreBA
//          println(s"Node: $nodeId got result from largecoreba $readyOutCoreBA its new readyOut $readyOut")
          Behaviors.same
        case BroadcastCoreBAResult(seqNum, readyOut, valueOut, senderID, replyTo) =>
          coreBAResult = coreBAResult + (senderID -> (readyOut, valueOut))
          replyTo ! Ack(seqNum)
          Behaviors.same
        case PromiseRequest(seqNum, replyTo) =>
//          println(s"I am $nodeId and I received the messageID: $seqNum from $replyTo")
          replyTo ! PromiseResponse(seqNum, readyOut, value)
          Behaviors.same
        case PromiseResponse(seqNum, readyOut, value) =>
          promiseResponses = promiseResponses :+ (readyOut, value)
//          println(s"I am node $nodeId new outstanding messageids $outstandingMessageIDs")
          receivedMessageId(seqNum)
          if (outstandingMessageIDs.isEmpty)
            broadCastSaves()
          Behaviors.same
        case Ack(seqNum) =>
          receivedMessageId(seqNum)
//          println(s"new outstanding messageids $outstandingMessageIDs")
          if (outstandingMessageIDs.isEmpty)
            broadCastSaves()
          Behaviors.same
        case Save(senderId, senderRoundID, senderAddress) =>
          saveMap(senderRoundID) = saveMap.getOrElse(senderRoundID, Set.empty) + senderId
//          println(s"I am $nodeId, this is round $roundID saved nodes $saveMap: ${saveMap(roundID).size}/$numberOfNodes")
          if (saveMap(roundID).size == numberOfNodes) {
            println(s"---------- DONE WITH ROUND $nodeId:$roundID ----------")
            resetSaveNodes(roundID)
            roundID = roundID + 1
            roundID match {
              case 3 =>
//                println(s"I am node $nodeId and I am light: $light")
                if (light) {
                  largeCoreBa(0) ! LargeCoBeRa.RegisterLightNode(nodeId)
                  val (selectedId, selectedAddress) = activeNodes.toList(r.nextInt(activeNodes.size))
                  activeNodes.foreach(entry => {
                    seqNum = seqNum + 1
                    outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                    entry._2 ! SelectedActiveID(seqNum, selectedId, selectedAddress, ctx.self)
                  })
                }
                else
                  broadCastSaves()
              case 4 =>
//                println(s"I am node $nodeId and I am active: $active")
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
//                          println(s"sending query message with seqnum $nodeId-$seqNum towards ${entry._1}")
                          outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                          ref ! Query(seqNum, entry._1, entry._2, nodeId, ctx.self)
                        })
                    })
                  }
                  else
                    broadCastSaves()
                }
                else
                  broadCastSaves()
              case 5 =>
                if (light) {
                  queryList
                    .filter(entry => activeNodes.contains(entry._1) && activeNodes.contains(entry._3))
                    .foreach(entry => {
                      seqNum = seqNum + 1
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
                    readyIn = if (n >= high) 1 else 0
                    seqNum = seqNum + 1
                    outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                    largeCoreBa(0) ! LargeCoBeRa.PrepareCoreBA(seqNum, nodeId, readyIn, n >= low, ctx.self)
                  }
                  else
                    broadCastSaves()
                }
                else
                  broadCastSaves()
              case 7 =>
                if (active) largeCoreBa(0) ! LargeCoBeRa.AgreementSelectionPhaseOne(ctx.self)
                else broadCastSaves()
              case 8 =>
                // PHASE 5
                if (active && readyOut == 1) largeCoreBa(0) ! LargeCoBeRa.AgreementSelectionPhaseTwo(ctx.self)
                else broadCastSaves()
              case 9 =>
                // PHASE 6
                if (active) {
                  nodesReferences.foreach { ref =>
                    seqNum = seqNum + 1
                    outstandingMessageIDs = outstandingMessageIDs :+ seqNum
                    ref ! BroadcastCoreBAResult(seqNum, readyOut, value, nodeId, ctx.self)
                  }
                }
                else broadCastSaves()
              case 10 =>
                // PHASE 6: Every node knows about all other decisions
                readyOut = if (selectedNodes.size >= low - T) coreBAResult.values.groupBy(entry => entry._1).maxBy(_._2.size)._1 else readyOut
                value = if (readyOut == 1) coreBAResult.values.groupBy(entry => entry._2).maxBy(_._2.size)._1 else value
                broadCastSaves()
              case 11 =>
                // PHASE 7
                val promiseAgreementNodes = r
                  .shuffle(nodesReferences)
                  .filter(ref => ref != ctx.self)
                  .take(math.round(C * math.log(numberOfNodes)).toInt)
//                println(s"agreementNodes: ${promiseAgreementNodes} nodereferences: ${nodesReferences}")
//                nodesReferences.filter(ref => !promiseAgreementNodes.contains(ref)).foreach(ref => {
//                  println(s"sending save to $ref")
//                  ref ! Save(nodeId = nodeId, replyTo = ctx.self)
//                })

                promiseAgreementNodes.foreach(ref => {
                  seqNum = seqNum + 1
                  outstandingMessageIDs = outstandingMessageIDs :+ seqNum
//                  println(s"I am node $nodeId, sending message with ID: $seqNum to $ref")
                  ref ! PromiseRequest(seqNum, ctx.self)
                })



                /**
                 * 1. Each node y sends a request to a random set of c log n nodes.
                 * 2.Each nodex, upon receiving a request from a nodey, responds to the request by reporting(ready-outx,valuex).
                 * 3.If greater than at/n+✏fraction of nodes sampled byxrespond withready-out=1, thenxsetsready-out1and setsvaluexto the majority of thevaluebits sent by sampled nodes. Elseready-outx0
                 */
              case 12 =>
                if (promiseResponses.count(response => response._1 == 1) > T/numberOfNodes) {
                  readyOut = 1
                  if (readyOut == 1) {
                    value = promiseResponses.groupBy(entry => entry._2).maxBy(_._2.size)._1
                    println(s"I am node $nodeId my readyout is: $readyOut my value is $value")
                    // terminate
//                    ctx.stop(self)
                  } else if (p < (1 / (C * math.log(numberOfNodes)))) {
                    // reset and perform with double p
                  } else {
                    // perform Byzantine Agreement
                  }
                } else {
                  readyOut = 0
                }


              case _ => println("I have not been implemented")
            }
          }
          Behaviors.same
      }
    }
  }

}
