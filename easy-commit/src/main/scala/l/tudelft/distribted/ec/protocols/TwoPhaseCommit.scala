package l.tudelft.distribted.ec.protocols

import io.vertx.core.AsyncResult
import io.vertx.core.buffer.Buffer
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.eventbus.Message
import l.tudelft.distribted.ec.HashMapDatabase

import scala.collection.mutable


abstract case class SupervisorState() extends Transaction

//TODO add timeouts
abstract class TwoPhaseCommit(
                               private val vertx: Vertx,
                               private val address: String,
                               private val database: HashMapDatabase,
                             ) extends Protocol(vertx, address, database) {

  protected val stateManager = new TransactionStateManager(address)

  /**
   * Transaction is requested by an external party.
   * This agent will act as the supervisor for this transaction.
   * First step is to send to all cohorts to prepare, next step is handled by `handleProtocolMessage`
   *
   * @param transaction the transaction to carry out.
   *                    TODO how is the transaction ID generated?
   */
  override def requestTransaction(transaction: Transaction): Unit = {
    // In this case this node is the supervisor
    if (stateManager.stateExists(transaction.id)) {
      // TODO perhaps do something with the old transaction? Queue?
    }

    if (!transactionPossible(transaction)) {
      // TODO log.
      return
    }

    stateManager.createState(transaction, address, ReceivingReadyState(transaction.id, mutable.HashSet(address)))
    sendToCohortExpectingReply(TransactionPrepareRequest(address, transaction.id, transaction), (reply: AsyncResult[Message[Buffer]]) => {
      if (reply.succeeded()) {
        reply.result().body().toJsonObject.mapTo(classOf[ProtocolMessage]) match {
          case TransactionReadyResponse(sender, id, _) => handleReadyResponse(reply.result(), sender, id)
          case TransactionAbortResponse(sender, id, _) => handleAbortResponse(reply.result(), sender, id)
          case _ =>
            abortTransaction(transaction.id)
        }
      } else {
        abortTransaction(transaction.id)
      }
    })
  }

  /**
   * Message has come in from one of the other cohorts.
   * This function decides which function should handle it.
   * Both supervisor- and supervised- targeted messages will come in,
   * meaning in some messages the agent should act as supervisor,
   * and in other messages as supervised.
   *
   * @param message         raw message received.
   * @param protocolMessage parsed message, matched to one of its subclasses.
   */
  override def handleProtocolMessage(message: Message[Buffer], protocolMessage: ProtocolMessage): Unit = protocolMessage match {
    case TransactionPrepareRequest(sender, _, transaction, _) => handlePrepareRequest(message, sender, transaction)
    case TransactionAbortResponse(sender, id, _) => handleAbortResponse(message, sender, id)
    case TransactionCommitRequest(sender, id, _) => handleCommitRequest(message, sender, id)
    case _ => handleUnrecognizedMessage(message, protocolMessage)
  }

  /**
   * A supervisor has asked this agent to prepare for a request.
   * Report back to the supervisor if this is possible.
   *
   * @param message     raw form of the message that was sent.
   * @param sender      the network address of the sender of the message.
   * @param transaction the transaction to prepare for.
   */
  def handlePrepareRequest(message: Message[Buffer], sender: String, transaction: Transaction): Unit = {
    val id = transaction.id
    if (stateManager.stateExists(id)) {
      val supervisor = stateManager.getSupervisor(id)
      if (sender != supervisor) {
        // Some faulty sender is trying to interfere with this transaction
        // Drop this request, and do not respond
        return
      } else {
        //TODO what to do if this transaction was already started by this supervisor?
        return
      }
    }

    if (transactionPossible(transaction)) {
      // Remember the READY response is being sent to the supervisor
      stateManager.createState(transaction, sender, ReadyState(id))
      replyToMessage(message, TransactionReadyResponse(address, id))
    } else {
      stateManager.createState(transaction, sender, AbortedState(id))
      replyToMessage(message, TransactionAbortResponse(address, id))
    }
  }

  /**
   * Handles the case that a READY package was sent to this agent.
   * Function only acts on this if this agent thinks it is the supervisor for this transaction.
   * If the number of READY's received from distinct senders
   * is equal to the number of agents in the network, this supervisor will commit.
   *
   * @param message raw form of the message that was sent. Used to directly reply.
   * @param sender  the network address of the sender of this message.
   * @param id      the id of the transaction the sender sent READY for.
   */
  def handleReadyResponse(message: Message[Buffer], sender: String, id: String): Unit = {
    if (!stateManager.stateExists(id)) {
      // Probably supervised this earlier but was deleted due to an abort
      return
    }

    // Check if this agent is the supervising entity.
    // If not, drop this request and do not respond.
    if (stateManager.getSupervisor(id) != address) {
      return
    }

    stateManager.getState(id) match {
      case ReceivingReadyState(_, confirmedAddresses) =>
        confirmedAddresses += sender

        // TODO check if network.size is in- or excluding this node
        if (confirmedAddresses.size < network.size) {
          stateManager.updateState(id, ReceivingReadyState(id, confirmedAddresses))
          return
        }

        // Global decision has been made, transfer commit decision to all cohorts
        commitTransaction(id)
      case _ =>
      // A READY was received by the supervisor, but the supervisor is not expecting it.
      // Drop the package.
      // TODO log that a wrong package was received
    }
  }

  /**
   * Handle when an ABORT message is sent to this agent.
   * Function only acts when this agent is the supervisor of the transaction,
   * or when the message is from the supervisor of this transaction.
   *
   * @param message raw form of the message that was sent. Used to directly reply.
   * @param sender  the sender of the ABORT message.
   * @param id      the transaction the ABORT message was sent about.
   */
  def handleAbortResponse(message: Message[Buffer], sender: String, id: String): Unit = {
    if (!stateManager.stateExists(id)) {
      // No state with this id, so abort what?
      //TODO log that a malicious package was found.
      return
    }

    val supervisor = stateManager.getSupervisor(id)
    if (supervisor == address || supervisor == sender) {
      // This agent is the supervisor of this transaction, or the message was from the supervisor
      abortTransaction(id)
    }
    // Else the abort came from an agent who sent the message for redundancy
  }

  /**
   * Handle committing of a transaction to the database.
   * If this message is from the supervisor, repeat the message to everyone
   * and update the database.
   *
   * @param message raw form of the message that was sent. Used to directly reply.
   * @param sender  the sender of the commit request.
   * @param id      the id of the transaction the commit was about.
   */
  def handleCommitRequest(message: Message[Buffer], sender: String, id: String): Unit = {
    if (!stateManager.stateExists(id)) {
      // No state with this id, so do not respond.
      //TODO log that a malicious package was found
      return
    }

    val state = stateManager.getState(id)
    if (state != ReadyState(id)) {
      // This state is not yet in the ready state.
      //TODO log that a malicious package was found.
      return
    }

    val supervisor = stateManager.getSupervisor(id)
    if (supervisor == sender && supervisor != address) {
      commitTransaction(id)
    }
  }

  /**
   * Commit the transaction to the database.
   * First remember the decision to commit, then commit,
   * then remember the commit was finished.
   *
   * @param id the id of the transaction to commit.
   */
  def commitTransaction(id: String): Unit = {
    stateManager.updateState(id, CommitDecidedState(id))
    stateManager.getTransaction(id) match {
      case RemoveDataTransaction(_, keyToRemove, _) => database.remove(keyToRemove)
      case StoreDataTransaction(_, keyToStore, data, _) => database.store(keyToStore, data)
    }
    stateManager.updateState(id, CommittedState(id))
  }

  /**
   * Abort the transaction.
   * First remember the decision to abort, then abort,
   * then remember the abort was finished.
   *
   * @param id the id of the transaction to abort.
   */
  def abortTransaction(id: String): Unit = {
    // Update everyone else, and stop tracking this.
    stateManager.updateState(id, AbortDecidedState(id))
    // TODO abort the transaction in the DB
    stateManager.updateState(id, AbortedState(id))
  }

  /**
   * Check if the current transaction can be executed.
   *
   * @param transaction the transaction to execute.
   */
  def transactionPossible(transaction: Transaction): Boolean = transaction match {
    case RemoveDataTransaction(_, keyToRemove, _) => database.retrieve(keyToRemove).isDefined
    case StoreDataTransaction(_, _, _, _) => true
  }

  /**
   * Handle an unrecognized message. Log that it was not recognized.
   *
   * @param protocolMessage the message that was not recognized.
   * @param sender the sender of the protocolMessage
   */
  def handleUnrecognizedMessage(message: Message[Buffer], protocolMessage: ProtocolMessage) = {
    // TODO print that a strange message was encountered
  }
}
