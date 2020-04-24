package executor
import core.data_structures.{BlockChainState, Record, Transaction}
import core.operations.Operation

import scala.collection.mutable

class SimpleSmartContract extends ISmartContract {
  override def execute(t: Transaction, state: BlockChainState): (Transaction, mutable.Set[Record]) = {
    t.getOperation match {
      case Operation.transfer => executeTransfer(t, state)
      case Operation.set => executeSet(t)
      case _ => {
        println("Operation not supported by smart contract")
        (t, mutable.Set[Record]())
      }
    }
  }

  /**
   * This function executes a Set Transaction
   *
   * @param transaction The Transaction that needs to be executed
   * @return A tuple containing the transaction and the records that resulted from its execution
   */
  def executeSet(transaction: Transaction): (Transaction, mutable.Set[Record]) = {
    val records: mutable.Set[Record] = mutable.Set[Record]()
    records.add(new Record(transaction.getAcc1, transaction.getAmount))
    (transaction, records)
  }

  /**
   * This function executes a Transfer Transaction.
   *
   * @param transaction The Transaction that needs to be executed
   * @param state The current blockchain state
   * @return A tuple containing the transaction and the records that resulted from its execution
   */
  def executeTransfer(transaction: Transaction, state: BlockChainState): (Transaction, mutable.Set[Record]) = {
    val records: mutable.Set[Record] = mutable.Set[Record]()
    val amount = transaction.getAmount
    val recipient: String = transaction.getAcc2 match {
      case Some(x) => x
      case _ => throw new IllegalArgumentException("Invalid transfer transaction")
    }
    val senderBalance = state.getBalance(transaction.getAcc1) match {
      case Some(x) => x
      case _ => 0
    }
    val recipientBalance = state.getBalance(recipient) match {
      case Some(x) => x
      case _ => 0
    }

    // Check whether the sender has enough balance to complete the transaction,
    // as well as whether the sender is not also the recipient
    if (senderBalance >= amount && transaction.getAcc1 != recipient) {
      records.add(new Record(transaction.getAcc1, senderBalance - amount))
      records.add(new Record(recipient, recipientBalance + amount))
    }
    // If the transaction can not be completed 'records' will be empty
    // This can be interpreted as the "abort" message from the paper
    (transaction, records)
  }
}
