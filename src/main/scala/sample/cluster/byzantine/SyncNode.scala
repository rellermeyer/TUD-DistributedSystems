package sample.cluster.byzantine

import akka.actor.ProviderSelection.Cluster
import akka.actor.TypedActor.self
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import sample.cluster.CborSerializable

import java.io.{BufferedWriter, FileWriter}
import scala.{:+, ::}
import scala.collection.mutable

object SyncNode {
  val SyncNodeServiceKey: ServiceKey[SyncNode.Event] = ServiceKey[SyncNode.Event]("SyncNodes")

  private final val C = 2
  final val EPISOLON = 0.2
  final val T = 1

  sealed trait Event
  private final case class NodesUpdated(newWorkers: Set[ActorRef[SyncNode.Event]]) extends Event
  private final case class LargeCoBeRaUpdated(largeCoBeRa: Set[ActorRef[LargeCoBeRa.Command]]) extends Event
  case object TimerKey extends Event

  sealed trait Request extends Event with CborSerializable
  case class MakeBad() extends Request
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

  def log2(x: Double) = scala.math.log(x)/scala.math.log(2)


  def apply(nodeId: Int, numberOfNodes: Int, root: ActorRef[App.Event], good: Boolean): Behavior[SyncNode.Event] = Behaviors.setup { ctx =>
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
    val p: Double = (C * log2(numberOfNodes))/numberOfNodes
    val low: Double = numberOfNodes - 2 * T - EPISOLON * numberOfNodes
    val high: Double = low + T
    val minA: Double = (1 - EPISOLON) * p * (numberOfNodes - T)
    val maxA: Double = (1 + EPISOLON) * p * (numberOfNodes - T)
    val beta: Double = (1 - EPISOLON) * (low - T) / (maxA + EPISOLON * p * numberOfNodes)
    val delta: Double = (1 - EPISOLON) * (low - T) / numberOfNodes
    var readyOut: Int = 0
    var readyIn: Int = 0
    var value: Int = 0
    println(s"p: $p")
    val active: Boolean = if (!good) true else r.nextDouble() <= p

    var light: Boolean = false

    // synchronization
    var roundID: Int = 2
    var outstandingMessageIDs: List[Int] = List.empty
    var seqNum: Int = 0

    // savemap -> roundID -> set[nodeIDs]
    val saveMap: mutable.Map[Int, Set[Int]] = mutable.Map.empty[Int, Set[Int]]

    def resetSaveNodes(roundID: Int): Unit = saveMap(roundID) = Set.empty
    def broadCastSaves(): Unit = nodesReferences.foreach(ref => ref ! Save(nodeId, roundID, ctx.self))

    def receivedMessageId(seqNum: Int): Unit = outstandingMessageIDs = outstandingMessageIDs.filter(_ != seqNum)

    def selectClogNNodes(): Int = math.round(C * math.log(numberOfNodes)).toInt

    def sendMessageByReference(ref: ActorRef[Event], message: Event): Unit = {
      outstandingMessageIDs = outstandingMessageIDs :+ seqNum
      seqNum = seqNum + 1
//      println(s"Node $nodeId which is $good good is sending a message, so seqNum is $seqNum.")
      ref ! message
    }

    // handling functions
    def handle_3a(): Unit = {
      if (light) {
        largeCoreBa(0) ! LargeCoBeRa.RegisterLightNode(nodeId)
        if (good) {
          val (selectedId, selectedAddress) = activeNodes.toList(r.nextInt(activeNodes.size))
          activeNodes.foreach(entry =>
            sendMessageByReference(entry._2, SelectedActiveID(seqNum, selectedId, selectedAddress, ctx.self))
          )
        } else {
          val (selectedId, selectedAddress) = activeNodes.toList(r.nextInt(activeNodes.size))
          activeNodes.foreach(entry =>
            // Poison other nodes by sending our own (bad) id many times (n * a times, as in the paper)
            for (i <- 1 to selectedNodes.size * activeNodes.size) {
              sendMessageByReference(entry._2, SelectedActiveID(seqNum, nodeId, selectedAddress, ctx.self))
            }
          )
          broadCastSaves()
        }
      }
      else
        broadCastSaves()
    }

    def handle_3b(): Unit = {
      if (active) {
        val n: Int = selectedNodes.size
        if (n > low - T) {
          // select the nodes that have their id sent by beta nodes
          activeNodes = selectedNodes.groupBy(tuple => tuple._1).filter(entry => entry._2.size >= beta)
            .values.flatten.toMap

          activeNodes.foreach(entry => {
            r.shuffle(nodesReferences).take(selectClogNNodes())
              .foreach(ref => sendMessageByReference(ref, Query(seqNum, entry._1, entry._2, nodeId, ctx.self)))
          })
        }
        else
          broadCastSaves()
      }
      else
        broadCastSaves()
    }

    def handle_3c(): Unit = {
      if (light) queryList
        .filter(entry => activeNodes.contains(entry._1) || activeNodes.contains(entry._3))
        .foreach(entry => sendMessageByReference(entry._4, QueryReply(seqNum, entry._1, ctx.self)))
      else
        broadCastSaves()
    }

    def handle_4_initialize(): Unit = {
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
    }

    def handle_4_agreement_selection_one(): Unit = {
      // query for ready out
      if (active) largeCoreBa(0) ! LargeCoBeRa.AgreementSelectionPhaseOne(ctx.self)
      else broadCastSaves()
    }

    def handle_5(): Unit = {
      // query for the value
      if (active && readyOut == 1) largeCoreBa(0) ! LargeCoBeRa.AgreementSelectionPhaseTwo(ctx.self)
      else broadCastSaves()
    }

    def handle_6_broadcast_results(): Unit = {
      if (active) nodesReferences
        .foreach(ref => sendMessageByReference(ref, BroadcastCoreBAResult(seqNum, readyOut, value, nodeId, ctx.self)))
      else broadCastSaves()
    }

    def handle_6_take_majority(): Unit = {
      println(s"I am node $nodeId and my coreBAResult is $coreBAResult.")
      readyOut = if (selectedNodes.size >= low - T) coreBAResult.values.groupBy(entry => entry._1).maxBy(_._2.size)._1 else readyOut
      value = if (readyOut == 1) coreBAResult.values.groupBy(entry => entry._2).maxBy(_._2.size)._1 else value
      println(s"I am node in handle 6 take majority $nodeId with $coreBAResult I am active: $active, I am light: $light")
      broadCastSaves()
    }

    def evaluation_report(): Unit = {
      val bw = new BufferedWriter(new FileWriter("evaluation_output.csv", true))
      bw.write(s"$nodeId,$roundID,$good,$seqNum,$active,$light,$value,$readyOut\n")
      bw.close()
    }

    def handle_7_promise_agreement(): Unit = {
      /**
       * 1. Each node y sends a request to a random set of c log n nodes.
       * 2.Each node x, upon receiving a request from a node y, responds to the request by reporting(ready-outx,valuex).
       * 3.If greater than at/n + episolon fraction of nodes sampled by x respond with ready-out=1, then xsets ready-out 1
       * and sets value x to the majority of the value bits sent by sampled nodes. Else ready-out x 0
       */
      val promiseAgreementNodes = r
        .shuffle(nodesReferences)
        .filter(ref => ref != ctx.self)
        .take(selectClogNNodes())

      promiseAgreementNodes.foreach(ref => sendMessageByReference(ref, PromiseRequest(seqNum, ctx.self)))
    }

    def handle_7_promise_agreement_result(): Unit = {
      if (promiseResponses.count(response => response._1 == 1) > T/numberOfNodes) readyOut = 1
      if (readyOut == 1) value = promiseResponses.groupBy(entry => entry._2).maxBy(_._2.size)._1
      if (readyOut == 1) {
//        println(s"I am node in handle 7 promise argeement $nodeId with $promiseResponses I am active: $active, I am light: $light, readyout: $readyOut, with value $value")
        // terminate
        evaluation_report()
        root ! App.cycleOutcome(nodeId = nodeId, valueX = value)
      }
      else if (p < (1 / (C * math.log(numberOfNodes)))) {
        // reset and perform with double p
        println(s"resetting from step 2 $nodeId")
      } else {
        // perform Byzantine Agreement
        activeNodes.foreach(entry => {
          nodesReferences.foreach(ref => sendMessageByReference(ref, Query(seqNum, entry._1, entry._2, nodeId, ctx.self)))
        })
        largeCoreBa(0) ! LargeCoBeRa.AgreementSelectionPhaseTwo(ctx.self)
        evaluation_report()
      }
    }

    Behaviors.withTimers { timers =>
      Behaviors.receiveMessage {
        case NodesUpdated(newWorkers) =>
          ctx.log.info("List of services registered with the receptionist changed: {}", newWorkers)
          nodesReferences = newWorkers.toIndexedSeq
          if (nodesReferences.size == numberOfNodes && active) nodesReferences.foreach(ref => {
            sendMessageByReference(ref, RegisterMyID(seqNum, nodeId, ctx.self))
          })
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
          println(s"coreba response: readyout $readyOutCoreBA, valueout $valueCoreBA")
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
          println(s"I am $nodeId, this is round $roundID saved nodes $saveMap ${saveMap.getOrElse(roundID, Set.empty).size}/$numberOfNodes")
          if (saveMap.getOrElse(roundID, Set.empty).size == numberOfNodes) {
            println(s"---------- DONE WITH ROUND $nodeId:$roundID ----------")
            resetSaveNodes(roundID)
            roundID = roundID + 1
            roundID match {
              case 3 => handle_3a()
              case 4 => handle_3b()
              case 5 => handle_3c()
              case 6 => handle_4_initialize()
              case 7 => handle_4_agreement_selection_one()
              case 8 => handle_5()
              case 9 => handle_6_broadcast_results()
              case 10 => handle_6_take_majority()
              case 11 => handle_7_promise_agreement()
              case 12 => handle_7_promise_agreement_result()
              case _ => println("I have not been implemented")
            }
          }
          Behaviors.same
      }
    }
  }

}
