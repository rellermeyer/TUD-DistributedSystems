package ledger

import core.data_structures.Record
import core.data_structures.BlockChainState

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.mutable

class StateSpec extends AnyFlatSpec with Matchers {
  "State" should "be initialised with empty set" in {
    val state = new BlockChainState()
    state.getState.size shouldEqual 0
  }

  "State" should "be able to contain an entry" in {
    var state = new BlockChainState()

    val entry1 = new Record("Jeff", 10)
    val entry2 = new Record("Linus", 20)
    val entries = mutable.Set[Record](entry1, entry2)

    state.addEntry("transaction_id", entries)

    state.getState.size shouldEqual 1
  }

  "State" should "not contain duplicate entries" in {
    var state = new BlockChainState()

    val entry1 = new Record("Jeff", 10)
    val entry2 = new Record("Linus", 20)
    val entries = mutable.Set[Record](entry1, entry2)

    state.addEntry("transaction_id", entries)
    state.addEntry("transaction_id", entries)

    state.getState.size shouldEqual 1
  }

  "State" should "be able to contain multiple entries" in {
    var state = new BlockChainState()

    val entry1 = new Record("Jeff", 10)
    val entry2 = new Record("Linus", 20)
    val entries = mutable.Set[Record](entry1, entry2)

    state.addEntry("transaction_id", entries)
    state.addEntry("other_transaction_id", entries)

    state.getState.size shouldEqual 2
  }
}
