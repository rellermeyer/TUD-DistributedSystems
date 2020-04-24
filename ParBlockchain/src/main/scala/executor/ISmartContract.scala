package executor

import core.data_structures.{BlockChainState, Record, Transaction}

import scala.collection.mutable

trait ISmartContract {
  def execute(t: Transaction, state: BlockChainState): (Transaction, mutable.Set[Record])
}
