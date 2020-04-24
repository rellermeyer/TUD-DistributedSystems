package actors

import actors.Client.{ChainInfoResponse, ClientReceivable}
import actors.Server.{ChainPositionUpdate, ClearDatabase, RegisteredServer, ServerReceivable, StartNewTailProcess}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.dispatch.ExecutionContexts
import communication.JsonSerializable

import scala.concurrent.duration._

object MasterService {

    sealed trait MasterServiceReceivable extends JsonSerializable
    final case class InitMasterService() extends MasterServiceReceivable
    final case class RequestChainInfo(replyTo: ActorRef[ClientReceivable]) extends MasterServiceReceivable
    final case class RegisterServer(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class RegisterTail(replyTo: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class Remove(server: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class Heartbeat(server: ActorRef[ServerReceivable]) extends MasterServiceReceivable
    final case class BroadCastClearDatabases() extends MasterServiceReceivable

    private var chain = List[ActorRef[ServerReceivable]]()
    private var clients = List[ActorRef[ClientReceivable]]()
    private var potentialTails = List[ActorRef[ServerReceivable]]()
    private var activeServers = Map[ActorRef[ServerReceivable], Boolean]()

    def apply(): Behavior[MasterServiceReceivable] = Behaviors.receive {
        (context, message) => {
            message match {
                case InitMasterService() => initMasterService(context, message)
                case RegisterServer(replyTo) => registerServer(context, message, replyTo)
                case RequestChainInfo(replyTo) => requestChainInfo(context, message, replyTo)
                case RegisterTail(replyTo) => registerTail(context, replyTo)
                case Heartbeat(replyTo) => heartbeat(context, message, replyTo)
                case BroadCastClearDatabases() => broadCastClearDatabases(context)
            }
        }
    }

    def broadCastClearDatabases(context: ActorContext[MasterServiceReceivable]): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: master service will send broadcast clear to all servers.")
        chain.foreach(chain => {
            chain ! ClearDatabase()
        })
        Behaviors.same
    }

    def initMasterService(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable): Behavior[MasterServiceReceivable] = {
        context.log.info("MasterService: master service is initialized.")

        // Remove inactive servers every 5 seconds
        context.system.scheduler.scheduleAtFixedRate(0.seconds, 5.seconds)(
            () => removeInactiveServers(context)
        )(ExecutionContexts.global())

        Behaviors.same
    }

    def registerServer(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        if (chain.isEmpty) {
            // Add head to chain initially.
            chain = chain :+ replyTo
            activeServers = activeServers.updated(replyTo, true)
        } else {
            // Otherwise, new server is potential tail first.
            context.log.info(s"MasterService: sending StartNewTailProcess to ${replyTo}")
            potentialTails = potentialTails :+ replyTo

            // Start the new tail process only if there is one, the other ones will be handled once the first tail is confirmed
            if (potentialTails.length == 1) {
                chain.last ! StartNewTailProcess(replyTo)
            }
        }

        replyTo ! RegisteredServer(context.self)

        // Send chainPositionUpdate to all the servers in the chain
        chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }

        context.log.info("MasterService: received a register request from a server, sent response.")
        Behaviors.same
    }

    def registerTail(context: ActorContext[MasterServiceReceivable], replyTo: ActorRef[ServerReceivable]): Behavior[MasterServiceReceivable] = {
        // Remove server from potential tails.
        potentialTails = potentialTails.filter(_ != replyTo)

        // Add server to chain as tail.
        chain = chain :+ replyTo
        activeServers = activeServers.updated(replyTo, true)

        // Send chainPositionUpdate to all the servers in the chain
        chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }

        // Start new tail process if there are more potential tails
        if (potentialTails.nonEmpty) {
            context.log.info("MasterService: starting the tail process for the next potential chain in the list")
            chain.last ! StartNewTailProcess(potentialTails.head)
        }

        context.log.info("MasterService: registering tail, sent response.")
        Behaviors.same
    }

    def requestChainInfo(context: ActorContext[MasterServiceReceivable], message: MasterServiceReceivable, replyTo: ActorRef[ClientReceivable]): Behavior[MasterServiceReceivable] = {
        replyTo ! ChainInfoResponse(chain.head, chain.last)
        clients = clients :+ replyTo
        context.log.info("MasterService: received a chain request from a client, sent info.")
        Behaviors.same
    }

    def chainPositionUpdate(context: ActorContext[MasterServiceReceivable],
                                 server: ActorRef[ServerReceivable], index: Int): Unit = {
        val isHead = index == 0
        val isTail = index == chain.length - 1
        val previous = chain(Math.max(index - 1, 0))
        val next = chain(Math.min(index + 1, chain.length - 1))
        context.log.info("MasterService sent {} chain position: isHead: {}, isTail: {}, previous: {} and next: {}", server, isHead, isTail, previous, next)
        server ! ChainPositionUpdate(isHead, isTail, previous, next)
        updateKnownClients(context)
    }

    def heartbeat(value: ActorContext[MasterServiceReceivable], receivable: MasterServiceReceivable, replyTo: ActorRef[Server.ServerReceivable]): Behavior[MasterServiceReceivable] = {
        activeServers = activeServers.updated(replyTo, true)

        Behaviors.same
    }

    def removeInactiveServers(context: ActorContext[MasterServiceReceivable]): Unit = {
        // Get all inactive servers
        val toRemove = chain.filter(actorRef => {
            val isActive = activeServers.get(actorRef)
            isActive match {
                case Some(true) => false
                case _ => true
            }
        })

        // Remove inactive servers from the chain and update all other servers
        if (toRemove.nonEmpty) {
            context.log.info("MasterService: Removing servers due to failing heartbeats. {}", toRemove)
            chain = chain.filter(actorRef => !toRemove.contains(actorRef))
            chain.zipWithIndex.foreach{ case (server, index) => chainPositionUpdate(context, server, index) }
        }

        // Reset activeServers
        activeServers = activeServers.empty
    }

    def updateKnownClients(context: ActorContext[MasterServiceReceivable]): Behavior[MasterServiceReceivable] = {
        clients.foreach(client => {
            client ! ChainInfoResponse(chain.head, chain.last)
        })
        Behaviors.same
    }


}
