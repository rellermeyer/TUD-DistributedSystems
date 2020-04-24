package core.data_structures

import scala.collection.mutable

class BlockChainState {
  private val state: mutable.Set[(String, mutable.Set[Record])] = mutable.Set[(String, mutable.Set[Record])]()
  private val balances: mutable.Map[String, Long] = mutable.Map[String, Long]()

  def addEntry(transactionId: String, records: mutable.Set[Record]): Unit = {
    state.add((transactionId, records))
    updateBalances(records)
  }

  def getBalance(id: String): Option[Long] = {
    balances.get(id)
  }

  def getBalances: mutable.Map[String, Long] = balances
  def getState: mutable.Set[(String, mutable.Set[Record])] = state

  def updateBalances(records: mutable.Set[Record]): Unit = {
    for (r <- records) {
      balances.put(r.getFieldId, r.getBalance)
    }
  }
}
