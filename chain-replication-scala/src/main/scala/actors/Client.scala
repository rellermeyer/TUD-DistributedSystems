package actors

import actors.MasterService.{BroadCastClearDatabases, RequestChainInfo}
import actors.Server.{Query, ServerReceivable, Update}
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import communication.JsonSerializable

import scala.collection.mutable
import scala.util.Random

object Client {

    sealed trait ClientReceivable extends JsonSerializable
    final case class InitClient(remoteMasterServicePath: String) extends ClientReceivable
    final case class ChainInfoResponse(head: ActorRef[ServerReceivable], tail: ActorRef[ServerReceivable]) extends ClientReceivable
    final case class QueryResponse(objId: Int, queryResult: Option[String]) extends ClientReceivable
    final case class UpdateResponse(objId: Int, newValue: String) extends ClientReceivable
    final case class CallQuery(objId: Int, options: Option[List[String]]) extends ClientReceivable
    final case class CallUpdate(objId: Int, newObj: String, options: Option[List[String]]) extends ClientReceivable
    final case class StressTest(totalMessages: Int, percentageUpdate: Int) extends ClientReceivable

    private var masterService: ActorSelection = _

    private var head: ActorRef[ServerReceivable] = _
    private var tail: ActorRef[ServerReceivable] = _

    private var start: Long = 0
    private var totalMessages: Int = Integer.MAX_VALUE
    private var messagesReceived: Int = 0

    def apply(): Behavior[ClientReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitClient(remoteMasterServicePath) => initClient(context, message, remoteMasterServicePath)
                case ChainInfoResponse(head, tail) => chainInfoResponse(context, message, head, tail)
                case QueryResponse(objId, queryResult) => queryResponse(context, message, objId, queryResult)
                case UpdateResponse(objId, newValue) => updateResponse(context, message, objId, newValue)
                case CallQuery(objId, options) => callQuery(context, message, objId, options)
                case CallUpdate(objId, newObj, options) => callUpdate(context, message, objId, newObj, options)
                case StressTest(totalMessages, percentageUpdate) =>
                    stressTest(context, message, totalMessages, percentageUpdate)
            }
        }
    }

    def initClient(context: ActorContext[ClientReceivable], message: ClientReceivable, remoteMasterServicePath: String): Behavior[ClientReceivable] = {
        masterService = context.toClassic.actorSelection(remoteMasterServicePath)
        masterService ! RequestChainInfo(context.self)

        context.log.info("Client: will try to get the chain's head and tail from the masterService {}.", masterService.pathString)
        Behaviors.same
    }

    def chainInfoResponse(context: ActorContext[ClientReceivable], message: ClientReceivable, head: ActorRef[ServerReceivable], tail: ActorRef[ServerReceivable]): Behavior[ClientReceivable] = {
        this.head = head
        this.tail = tail

        context.log.info("Client: received a ChainInfoResponse, head: {}, tail: {}", head.path, tail.path)
        Behaviors.same
    }

    def queryResponse(context: ActorContext[ClientReceivable], message: ClientReceivable, objId: Int, queryResult: Option[String]): Behavior[ClientReceivable] = {
        queryResult match {
            case Some(value) => context.log.info("Client: received a QueryResponse for objId {} = {}", objId, value)
            case None => context.log.info("Client: no result found for objId {}", objId)
        }

        incrementMessage(context)

        Behaviors.same
    }

    def updateResponse(context: ActorContext[ClientReceivable], message: ClientReceivable, objId: Int, newValue: String): Behavior[ClientReceivable] = {

        incrementMessage(context)

        Behaviors.same
    }

    def callQuery(context: ActorContext[ClientReceivable], message: ClientReceivable,
                  objId: Int, options: Option[List[String]]): Behavior[ClientReceivable] = {
        println(s"Client: sending query to tail $tail")
        this.tail ! Query(objId, options, context.self)
        Behaviors.same
    }

    def callUpdate(context: ActorContext[ClientReceivable], message: ClientReceivable,
                   objId: Int, newObj: String, options: Option[List[String]]): Behavior[ClientReceivable] = {
        println(s"Client: sending update to head $head")
        this.head ! Update(objId, newObj, options, context.self, this.head)
        Behaviors.same
    }

    def stressTest(context: ActorContext[ClientReceivable], message: ClientReceivable,
                   totalMessages: Int, percentageUpdate: Int): Behavior[ClientReceivable] = {

        context.log.info(s"Stress test called with $totalMessages total messages and $percentageUpdate% update messages")

        val messageQueue: mutable.Queue[ClientReceivable] = mutable.Queue()
        for (i <- 1 to totalMessages) {
            // Random value between 0 and 100
            val r = new Random().nextInt(100)

            // A percentage of the messages will be updates, and the rest will be queries.
            if (r < percentageUpdate) {
                val jsonObject = s"""{"$i": $i}"""
                messageQueue.enqueue(CallUpdate(i, jsonObject, None))
            } else {
                messageQueue.enqueue(CallQuery(i, None))
            }
        }

        this.start = System.currentTimeMillis()
        this.totalMessages = totalMessages
        this.messagesReceived = 0

        for (message <- messageQueue) {
            context.self ! message
        }

        Behaviors.same
    }

    private def incrementMessage(context: ActorContext[ClientReceivable]): Unit = {
        this.messagesReceived += 1

        // The totalMessages is only set to a relevant value after calling `stresstest` from the console.
        if (this.messagesReceived >= this.totalMessages) {
            val end = System.currentTimeMillis()
            val duration = end - start
            context.log.info(s"Executing $totalMessages queries took: %d".format(duration))
            masterService ! BroadCastClearDatabases()
        }
    }
}
