package ledger

import core.applications.Application
import core.data_structures.BlockChainBlock
import core.data_structures.Transaction
import core.operations.Operation
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable

class BlockSpec extends AnyFlatSpec with Matchers {
  val app: Application.Value = Application.A

  "Block" should "be initialised okay" in {
    val block = new BlockChainBlock("0", 0)
    block.getPreviousHash shouldEqual "0"
    block.getSequenceId shouldEqual 0
  }

  "Adding transaction" should "increase length of transaction array" in {
    val block = new BlockChainBlock("0", 0)
    block.getTransactions.length shouldEqual 0
    val t = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block.addTransaction(t)
    block.getTransactions.length shouldEqual 1
  }

  "Tostring identical blocks" should "return same string" in {
    val block = new BlockChainBlock("0", 0)
    val t = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block.addTransaction(t)

    val block2 = new BlockChainBlock("0", 0)
    val t2 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block2.addTransaction(t2)
    block.toString() shouldEqual block2.toString()
  }

  "Hashing identical blocks" should "return same string" in {
    val block = new BlockChainBlock("0", 0)
    val t = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block.addTransaction(t)

    val block2 = new BlockChainBlock("0", 0)
    val t2 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block2.addTransaction(t2)
    block.hash() shouldEqual block2.hash()
  }

  "Hashing different blocks" should "return different string" in {
    val block = new BlockChainBlock("0", 0)
    val t = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block.addTransaction(t)

    val block2 = new BlockChainBlock("1", 0)
    val t2 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    block2.addTransaction(t2)
    assert(!block.hash().equals(block2.hash()))
  }
}
