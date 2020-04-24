package example

import core.Config
import core.data_structures.BlockChainLedger
import core.data_structures.BlockChainBlock

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LedgerSpec extends AnyFlatSpec with Matchers {
  "Ledger" should "be initialised with genesis" in {
    var ledger = new BlockChainLedger()
    ledger.getLedger.length shouldEqual 1
    ledger.getLedger(0) shouldEqual Config.genesisBlock
  }

  "Ledger" should "validate correctly" in {
    var ledger = new BlockChainLedger()
    var newBlock = new BlockChainBlock(ledger.getLedger(0).hash(), ledger.getLedger(0).getSequenceId + 1)
    ledger.addBlock(newBlock)
    ledger.validate() shouldEqual true
  }

  "Ledger" should "not validate if invalid sequence id" in {
    var ledger = new BlockChainLedger()
    var newBlock = new BlockChainBlock(ledger.getLedger(0).hash(), ledger.getLedger(0).getSequenceId + 2)
    ledger.addBlock(newBlock)
    ledger.getLedger.length shouldEqual 2
    assert(!ledger.validate())
  }

  "Ledger" should "not validate if invalid hash" in {
    var ledger = new BlockChainLedger()
    var newBlock = new BlockChainBlock(ledger.getLedger(0).hash() + " ", ledger.getLedger(0).getSequenceId + 1)
    ledger.addBlock(newBlock)
    ledger.getLedger.length shouldEqual 2
    assert(!ledger.validate())
  }
}
