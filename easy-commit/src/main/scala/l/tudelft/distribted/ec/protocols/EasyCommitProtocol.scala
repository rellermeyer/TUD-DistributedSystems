package l.tudelft.distribted.ec.protocols

import io.vertx.scala.core.Vertx
import l.tudelft.distribted.ec.HashMapDatabase
import l.tudelft.distribted.ec.protocols.NetworkState.NetworkState

import scala.collection.mutable

/**
 * Easy commit protocol, implemented by example of paper by Gupta & Sadoghi (2018).
 *
 * @param vertx the message bus over which to communicate.
 * @param address the network address of this agent.
 * @param database the database on which to execute the transactions.
 */
class EasyCommitProtocol(
    private val vertx: Vertx,
    private val address: String,
    private val database: HashMapDatabase,
  ) extends TwoPhaseCommit(vertx, address, database) {


  /**
   * Commit to the database. Let everyone know this is being done.
   *
   * @param id the id of the transaction to commit.
   */
  override def commitTransaction(id: String): Unit = {
    // Update everyone else, and commit to DB.
    stateManager.updateState(id, CommitDecidedState(id))
    sendToCohort(TransactionCommitRequest(address, id))
    performTransaction(stateManager.getTransaction(id))
    stateManager.updateState(id, CommittedState(id))
  }


  /**
   * Abort the transaction to the DB. Let everyone know this is being done.
   *
   * @param id the id of the transaction to abort.
   */
  override def abortTransaction(id: String): Unit = {
    // Update everyone else, and stop tracking this.
    (stateManager.getState(id)) match {
      case AbortedState(_) =>
      case _ =>
        stateManager.updateState(id, AbortDecidedState(id))
        sendToCohort(TransactionAbortResponse(address, id))
        revertTransaction(stateManager.getTransaction(id))
        stateManager.updateState(id, AbortedState(id))
    }
  }
}
