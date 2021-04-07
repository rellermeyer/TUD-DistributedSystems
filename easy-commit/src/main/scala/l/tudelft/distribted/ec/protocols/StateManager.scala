package l.tudelft.distribted.ec.protocols

import scala.collection.mutable

trait PhaseState {
  def id: String
}

// State of supervisor in which it is counting how many agents agreed with the transaction.
case class ReceivingReadyState(id: String, receivedAddresses: mutable.HashSet[String]) extends PhaseState

// State of cohort after receiving a PREPARE message and deciding to be ready
case class ReadyState(id: String) extends PhaseState

// State of cohort or supervisor after having decided what should be done
case class CommitDecidedState(id: String) extends PhaseState
case class AbortDecidedState(id: String) extends PhaseState

// State of cohort or supervisor after having done what was decided
case class CommittedState(id: String) extends PhaseState
case class AbortedState(id: String) extends PhaseState

/**
 * Tracks the state and supervisor of each transaction.
 * Makes sure there are no discrepancies between its to fields.
 * TODO Temporary fix with hashmap until we fix a real log.
 *
 * @param address the address of the agent creating this manager.
 */
class TransactionStateManager (private val address: String){
  // Link between a transaction ID and its supervisor
  private val supervisorMap = new mutable.HashMap[String, String]()

  // Link between a transaction ID and the state this cohort is in
  private val stateMap = new mutable.HashMap[String, PhaseState]()

  // Link between a transaction ID and the actual transaction.
  private val transactionIdMap = new mutable.HashMap[String, Transaction]()

  /**
   * Create a new state to track the state of a transaction.
   * @param id the id of the transaction to track.
   * @param supervisor the network address of the supervisor.
   */
  def createState(transaction: Transaction, supervisor: String, state: PhaseState): Unit = {
    supervisorMap.put(transaction.id, supervisor)
    stateMap.put(transaction.id, state)
    transactionIdMap.put(transaction.id, transaction)
  }

  /**
   * Update the state some transaction is in.
   * This function should be used instead of changing stateMap directly.
   *
   * @param id the id of the transaction to update the state of.
   * @param state the new state of the transaction.
   */
  def updateState(id: String, state: PhaseState): Unit = {
    if (!stateExists(id)) {
      throw new IllegalArgumentException("No state exists for id: " + id)
    }
    stateMap.put(id, state)
  }

  /**
   * Get the supervisor of a transaction.
   * CAUTION: Will throw an error if transaction is not being tracked.
   * Use `stateExists` to check whether a record with this ID exists.
   *
   * @param id the id of the transaction to get the supervisor of.
   * @return the network address of the supervisor of this transaction.
   */
  def getSupervisor(id: String): String = supervisorMap(id)

  /**
   * Get the state of a transaction.
   * CAUTION: Will throw an error if transaction is not being tracked.
   * Use `stateExists` to check whether a record with this ID exists.
   *
   * @param id the id of the transaction to get the state of.
   * @return the state this transaction is in.
   */
  def getState(id: String): PhaseState = stateMap(id)

  /**
   * Get the transaction with a given ID.
   * CAUTION: Will throw an error if transaction is not being tracked.
   * Use `stateExists` to check whether a record with this ID exists.
   *
   * @param id the id of the transaction to get the state of.
   * @return transaction belonging to this id.
   */
  def getTransaction(id: String): Transaction = transactionIdMap(id)

  /**
   * Check whether a given transaction is being tracked by this class.
   * If there is a discrepancy between stateMap and supervisorMap, false is returned.
   * @param id the id of the transaction to check if it exists.
   * @return true if this transaction is being tracked, false else.
   */
  def stateExists(id: String): Boolean = {
    val superExists = supervisorMap.contains(id)
    val stateExists = stateMap.contains(id)
    val transactionExists = transactionIdMap.contains(id)

    if (superExists && stateExists && transactionExists) {
      return true
    }
    // Illegal state: discrepancy between supervisorMap and stateMap
    // Fix it by deleting the existing key
    if (superExists) {
      supervisorMap.remove(id)
    }
    if (stateExists) {
      stateMap.remove(id)
    }
    if (transactionExists) {
      transactionIdMap.remove(id)
    }
    false
  }
}
