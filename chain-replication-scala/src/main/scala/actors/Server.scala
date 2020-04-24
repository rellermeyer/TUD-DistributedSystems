package actors

import actors.Client.{ClientReceivable, QueryResponse, UpdateResponse}
import actors.MasterService.{Heartbeat, MasterServiceReceivable, RegisterServer, RegisterTail}
import actors.Server.unSentToNewTail
import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.dispatch.ExecutionContexts
import communication.JsonSerializable
import storage.Storage

import scala.concurrent.duration._

object Server {

    sealed trait ServerReceivable extends JsonSerializable
    final case class InitServer(remoteMasterServicePath: String) extends ServerReceivable
    final case class Query(objId: Int, options: Option[List[String]], from: ActorRef[ClientReceivable]) extends ServerReceivable
    final case class Update(objId: Int, newObj: String, options: Option[List[String]], from: ActorRef[ClientReceivable], previous: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class UpdateAcknowledgement(objId: Int, newObj: String, next: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class RegisteredServer(masterService: ActorRef[MasterServiceReceivable]) extends ServerReceivable
    final case class StartNewTailProcess(newTail: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class TransferUpdate(objId: Int, obj: String, replyTo: ActorRef[ServerReceivable]) extends ServerReceivable
    final case class TransferAck(objId: Int, obj: String) extends ServerReceivable
    final case class TransferComplete() extends ServerReceivable
    final case class ClearDatabase() extends ServerReceivable
    final case class ChainPositionUpdate(isHead: Boolean,
                                         isTail: Boolean,
                                         previous: ActorRef[ServerReceivable],
                                         next: ActorRef[ServerReceivable]
                                        ) extends ServerReceivable


    private var inProcess: List[ServerReceivable] = List()
    private var masterService: ActorSelection = _
    private var storage: Storage = _

    private var isHead: Boolean = _
    private var isTail: Boolean = _
    private var previous: ActorRef[ServerReceivable] = _
    private var next: ActorRef[ServerReceivable] = _

    private var newTailProcess = false
    private var newTail: ActorRef[Server.ServerReceivable] = _
    private var unSentToNewTail: List[(Int, String)] = List()

    def apply(): Behavior[ServerReceivable] = Behaviors.receive {
        (context, message) =>
            message match {
                case InitServer(remoteMasterServicePath) => initServer(context, message, remoteMasterServicePath)
                case RegisteredServer(masterService) => registeredServer(context, message, masterService)
                case Update(objId, newObj, options, from, self) =>
                    if(!this.isTail) {
                        inProcess = Update(objId, newObj, options, from, next) :: inProcess
                    }
                    update(context, message, objId, newObj, options, from)
                case UpdateAcknowledgement(objId, newObj, next) =>
                    processAcknowledgement(UpdateAcknowledgement(objId, newObj, next), context.self)
                case Query(objId, options, from) => query(context, message, objId, options, from)
                case ChainPositionUpdate(isHead, isTail, previous, next) => chainPositionUpdate(context, message, isHead, isTail, previous, next)
                case StartNewTailProcess(newTail) => startNewTailProcess(context, newTail)
                case TransferUpdate(objId, obj, replyTo) => transferUpdate(context, objId, obj, replyTo)
                case TransferAck(objId, obj) => transferAck(context, objId, obj)
                case TransferComplete() => transferComplete(context)
                case ClearDatabase() => clearDatabase(context)
            }
    }

    def clearDatabase(context: ActorContext[ServerReceivable]): Behavior[ServerReceivable] = {
        storage.clear()

        context.log.info(s"Server: received request to clear database, will do so.")
        Behaviors.same
    }

    def startNewTailProcess(context: ActorContext[ServerReceivable], newTail: ActorRef[ServerReceivable]): Behavior[ServerReceivable] = {
        context.log.info(s"Server: received start new tail process")

        this.newTailProcess = true
        this.newTail = newTail

        // Retrieve all objects from database.
        unSentToNewTail = storage.getAllObjects

        transferUnsentUpdatesToNewTail(context, unSentToNewTail)

        Behaviors.same
    }

    def transferAck(context: ActorContext[ServerReceivable], objId: Int, obj: String): Behavior[ServerReceivable] = {
        context.log.info(s"Server: received ack for ${objId}")

        // Received the ack, so remove the item from the list.
        unSentToNewTail = unSentToNewTail.drop(1)

        transferUnsentUpdatesToNewTail(context, unSentToNewTail)

        Behaviors.same
    }

    def transferUnsentUpdatesToNewTail(context: ActorContext[ServerReceivable], unSentToNewTail: List[(Int, String)]): Unit = {
        if (unSentToNewTail.nonEmpty) {
            // Not all items are sent, send more.
            val itemToSend = unSentToNewTail.head
            context.log.info(s"Server: sending next item ${itemToSend._1} to new tail.")
            newTail ! TransferUpdate(itemToSend._1, itemToSend._2, context.self)
        } else {
            context.log.info(s"Server: all items were sent to new tail.")
            // All items are sent, inform new tail it is complete.
            newTail ! TransferComplete()

            // Send the in process messages to the new tail.
            inProcess.foreach(newTail ! _)

            // Reset the variables, since we know we are not tail anymore.
            this.newTail = null
            this.newTailProcess = false
        }
    }

    def transferUpdate(context: ActorContext[ServerReceivable], objId: Int, obj: String, replyTo: ActorRef[ServerReceivable]): Behavior[ServerReceivable] = {
        context.log.info(s"Server: received transfer update for ${objId}, sending ack back to ${replyTo}")

        // Storing the change in storage.
        storage.update(objId, obj, None)

        // Replying with ack.
        replyTo ! TransferAck(objId, obj)

        Behaviors.same
    }

    def transferComplete(context: ActorContext[ServerReceivable]): Behavior[ServerReceivable] = {
        context.log.info(s"Server: full transfer complete, will re-brand as the new tail and inform master.")

        masterService ! RegisterTail(context.self)
        Behaviors.same
    }

    def initServer(context: ActorContext[ServerReceivable], message: ServerReceivable, remoteMasterServicePath: String): Behavior[ServerReceivable] = {
        masterService = context.toClassic.actorSelection(remoteMasterServicePath)
        // TODO: Check if masterService is defined and stop the server if not.

        val fileName = context.self.path.toStringWithAddress(context.system.address).hashCode.toString
        storage = new Storage(fileName)

        masterService ! RegisterServer(context.self)

        context.log.info("Server: registering server at master service.")
        Behaviors.same
    }

    def registeredServer(context: ActorContext[ServerReceivable], message: ServerReceivable, masterService: ActorRef[MasterServiceReceivable]): Behavior[ServerReceivable] = {
        context.log.info("Server: server is registered at {}.", masterService.path)

        // Send a heartbeat to the masterservice every 2 seconds.
        context.system.scheduler.scheduleAtFixedRate(0.seconds, 2.seconds)(() => {
            masterService ! Heartbeat(context.self)
        })(ExecutionContexts.global())

        Behaviors.same
    }

    def chainPositionUpdate(context: ActorContext[ServerReceivable], message: ServerReceivable,
                            isHead: Boolean, isTail: Boolean,
                            previous: ActorRef[ServerReceivable], next: ActorRef[ServerReceivable]
                           ): Behavior[ServerReceivable] = {
        context.log.info("Server: server received chain position update, isHead {}, isTail {}, previous {} and next {}.",
            isHead, isTail, previous, next)
        this.isHead = isHead
        this.isTail = isTail
        this.previous = previous

        if(this.next != next && !this.isTail){
            this.next = next
            forwardUpdates(context)
        }
        this.next = next
        Behaviors.same
    }

    def query(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, options: Option[List[String]], from: ActorRef[ClientReceivable]): Behavior[ServerReceivable] = {
        val result = storage.query(objId, options)

        from ! QueryResponse(objId, result)
        context.log.info("Server: sent a query response for objId {} = {}.", objId, result)

        Behaviors.same
    }


    def update(context: ActorContext[ServerReceivable], message: ServerReceivable, objId: Int, newObj: String, options: Option[List[String]], from: ActorRef[ClientReceivable]): Behavior[ServerReceivable] = {
        val result = storage.update(objId, newObj, options)

        result match {
            case Some(newObj) =>
                if (this.isTail) {
                    if (newTailProcess) {
                        context.log.info(s"Server: add ${objId} update to inProcess, to later send to new tail.")
                        inProcess = Update(objId, newObj, options, from, this.next) :: inProcess
                    }

                    from ! UpdateResponse(objId, newObj)
                    this.previous ! UpdateAcknowledgement(objId, newObj, context.self)
                    context.log.info("Server: sent a update response for objId {} = {} as {}.", objId, newObj, context.self)
                } else {
                    this.next ! Update(objId, newObj, options, from, this.next)
                    context.log.info("Server: forward a update response for objId {} = {} as {} to {}.", objId, newObj, context.self, this.next)
                }
            case None => context.log.info("Something went wrong while updating {}", objId)
        }

        Behaviors.same
    }

    def processAcknowledgement(ack: UpdateAcknowledgement, self: ActorRef[ServerReceivable]): Behavior[ServerReceivable] = {
        val results = inProcess.filter {
            case Update(objId, newObj, options, from, previous) => !(objId == ack.objId && newObj == ack.newObj)
            case _ => true
        }
        inProcess = results
        if (!this.isHead) {
            this.previous ! UpdateAcknowledgement(ack.objId, ack.newObj, self)
        }
        Behaviors.same
    }

    def forwardUpdates(context: ActorContext[ServerReceivable]) = {
        inProcess.foreach(unAcknowledgedUpdate => {
            this.next ! unAcknowledgedUpdate
            context.log.info("forwarding {}", unAcknowledgedUpdate)
        })
    }

}
