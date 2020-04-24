package executorTest

import core.Config
import core.applications.Application
import core.applications.Application.Application
import core.data_structures.{BlockChainBlock, DependencyGraph, Transaction}
import core.operations.Operation
import org.scalatest._
import org.scalatest.matchers.should.Matchers
import executor.Executor

import scala.collection.{immutable, mutable}

class executorSpec extends FlatSpec with Matchers {
  "Executor" should "be initialised okay" in {
    val ex = new Executor(mutable.Set(), mutable.Map[String, mutable.Set[Application]](), "ex1", immutable.Seq[String](), immutable.Seq[String]())
    ex.lastSequenceId shouldBe 0
    ex.applications.size shouldBe 0
    ex.committedTransactions.size shouldBe 0
    ex.executedTransactions.size shouldBe 0
    ex.executionSet.size shouldBe 0
    ex.ledger.getLedger.length shouldBe 1
    ex.state.getBalances.size shouldBe 0
    ex.state.getState.size shouldBe 0
  }

  "Executor" should "be able to add block" in {
    val ex = new Executor(mutable.Set(Application.A), mutable.Map[String, mutable.Set[Application]](), "ex1", immutable.Seq[String](), immutable.Seq[String]())
    val new_block = new BlockChainBlock(Config.genesisBlock.hash(), Config.genesisBlock.getSequenceId + 1)
    val transaction1 = new Transaction(0, Operation.set, "test1", None, immutable.Seq[String](), immutable.Seq("test1"), 2, Application.A)
    val transaction2 = new Transaction(0, Operation.transfer, "test1", Some("test2"), immutable.Seq("test1", "test2"), immutable.Seq("test2"), 1, Application.A)
    new_block.addTransaction(transaction1)
    new_block.addTransaction(transaction2)
    val depGraph = new DependencyGraph(Config.genesisBlock.getSequenceId + 1)
    depGraph.addEdge(transaction1, transaction2)
    ex.newBlock(new_block.getSequenceId, new_block, depGraph, Set(Application.A), "orderId", new_block.hash())

    ex.lastSequenceId shouldBe new_block.getSequenceId
    ex.blockFinished shouldBe true
    ex.ledger.getLedger.length shouldBe 2
    ex.state.getBalance("test1") shouldBe Some(1)
    ex.state.getBalance("test2") shouldBe Some(1)
  }

  "Executor" should "set balances correctly" in {
    val ex = new Executor(mutable.Set(Application.A), mutable.Map[String, mutable.Set[Application]](), "ex1", immutable.Seq[String](), immutable.Seq[String]())
    val new_block = new BlockChainBlock(Config.genesisBlock.hash(), Config.genesisBlock.getSequenceId + 1)
    val transaction1 = new Transaction(0, Operation.set, "test1", None, immutable.Seq[String](), immutable.Seq("test1"), 2, Application.A)
    val transaction2 = new Transaction(0, Operation.set, "test1", None, immutable.Seq[String](), immutable.Seq("test1"), 1, Application.A)
    new_block.addTransaction(transaction1)
    new_block.addTransaction(transaction2)
    val depGraph = new DependencyGraph(Config.genesisBlock.getSequenceId + 1)
    depGraph.addEdge(transaction1, transaction2)
    ex.newBlock(new_block.getSequenceId, new_block, depGraph, Set(Application.A), "orderId", new_block.hash())

    ex.lastSequenceId shouldBe new_block.getSequenceId
    ex.blockFinished shouldBe true
    ex.ledger.getLedger.length shouldBe 2
    ex.state.getBalance("test1") shouldBe Some(1)
  }

  "Executor" should "should only execute transactions corresponding with their Applications" in {
    val ex = new Executor(mutable.Set(Application.A), mutable.Map[String, mutable.Set[Application]](), "ex1", immutable.Seq[String](), immutable.Seq[String]())
    val new_block = new BlockChainBlock(Config.genesisBlock.hash(), Config.genesisBlock.getSequenceId + 1)
    val transaction1 = new Transaction(0, Operation.set, "test1", None, immutable.Seq[String](), immutable.Seq("test1"), 2, Application.A)
    val transaction2 = new Transaction(0, Operation.set, "test1", None, immutable.Seq[String](), immutable.Seq("test1"), 1, Application.B)
    new_block.addTransaction(transaction1)
    new_block.addTransaction(transaction2)
    val depGraph = new DependencyGraph(Config.genesisBlock.getSequenceId + 1)
    depGraph.addEdge(transaction1, transaction2)

    ex.newBlock(new_block.getSequenceId, new_block, depGraph, Set(Application.A, Application.B), "orderId", new_block.hash())

    ex.lastSequenceId shouldBe new_block.getSequenceId
    ex.blockFinished shouldBe true
    ex.ledger.getLedger.length shouldBe 2
    ex.state.getBalance("test1") shouldBe Some(2)
  }

  "Executor" should "execute transactions in the correct order when dependencies exist" in {
    val ex = new Executor(mutable.Set(Application.A), mutable.Map[String, mutable.Set[Application]](), "ex1", immutable.Seq[String](), immutable.Seq[String]())
    val new_block = new BlockChainBlock(Config.genesisBlock.hash(), Config.genesisBlock.getSequenceId + 1)
    val transaction1 = new Transaction(0, Operation.set, "test1", None, immutable.Seq[String](), immutable.Seq("test1"), 2, Application.A)
    val transaction2 = new Transaction(0, Operation.set, "test2", None, immutable.Seq[String](), immutable.Seq("test2"), 1, Application.A)
    val transaction3 = new Transaction(0, Operation.transfer, "test2", Option("test1"), immutable.Seq("test2", "test1"),
                                       immutable.Seq("test1", "test2"), 1, Application.A)
    new_block.addTransaction(transaction1)
    new_block.addTransaction(transaction2)
    new_block.addTransaction(transaction3)

    val depGraph = new DependencyGraph(Config.genesisBlock.getSequenceId + 1)
    depGraph.addEdge(transaction1, transaction3)
    depGraph.addEdge(transaction2, transaction3)
    ex.newBlock(new_block.getSequenceId, new_block, depGraph, Set(Application.A), "orderId", new_block.hash())

    ex.lastSequenceId shouldBe new_block.getSequenceId
    ex.blockFinished shouldBe true
    ex.ledger.getLedger.length shouldBe 2
    ex.state.getBalance("test1") shouldBe Some(3)
    ex.state.getBalance("test2") shouldBe Some(0)
  }
}
