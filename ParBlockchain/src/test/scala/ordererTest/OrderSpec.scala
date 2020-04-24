package ordererTest

import core.applications.Application
import core.data_structures.Transaction
import core.operations.Operation
import orderer.Order
import org.scalatest._
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable

class OrderSpec extends FlatSpec with Matchers {

  "Orderer" should "be able to add transactions to a block" in {
    val order = new Order("test", "", immutable.Seq[String](), true, immutable.Seq[String](), immutable.Seq[String]())
    val t1 = new Transaction(0, Operation.set, "test1", None, immutable.Seq("test1"), immutable.Seq("test1"), 1, Application.A)
    val t2 = new Transaction(0, Operation.transfer, "test1", Some("test2"), immutable.Seq("test1", "test2"), immutable.Seq("test2"), 1, Application.A)
    order.addTransactionToBlock(t1)
    order.addTransactionToBlock(t2)

    order.currentBlock.getTransactions.length shouldBe 2
    order.currSequenceId shouldBe 2
    order.transactionOrder(t1) shouldBe 0
    order.transactionOrder(t2) shouldBe 1
  }

  "Orderer" should "be able to generate a correct DependencyGraph" in {
    val order = new Order("test", "", immutable.Seq[String](), true, immutable.Seq[String](), immutable.Seq[String]())
    val t0 = new Transaction(0, Operation.transfer, "test1", Some("test3"), immutable.Seq("test1", "test3"), immutable.Seq("test3"), 1, Application.A)
    val t1 = new Transaction(0, Operation.set, "test1", None, immutable.Seq("test1"), immutable.Seq("test1"), 1, Application.A)
    val t2 = new Transaction(0, Operation.transfer, "test1", Some("test2"), immutable.Seq("test1", "test2"), immutable.Seq("test2"), 1, Application.A)
    val t3 = new Transaction(0, Operation.set, "test3", None, immutable.Seq("test3"), immutable.Seq("test3"), 3, Application.A)
    val t4 = new Transaction(0, Operation.transfer, "test2", Some("test3"), immutable.Seq("test2", "test3"), immutable.Seq("test2", "test3"), 1, Application.A)
    val t5 = new Transaction(0, Operation.set, "test3", None, immutable.Seq("test3"), immutable.Seq("test3"), 2, Application.A)

    order.addTransactionToBlock(t0)
    order.addTransactionToBlock(t1)
    order.addTransactionToBlock(t2)
    order.addTransactionToBlock(t5)
    order.addTransactionToBlock(t3)
    order.addTransactionToBlock(t4)

    val graph = order.generateDependencyGraph(order.currentBlock)

    assert(graph.getPredecessors(t2).contains(t1))
    graph.getPredecessors(t2).size shouldBe 1

    assert(graph.getPredecessors(t4).contains(t2))
    assert(graph.getPredecessors(t4).contains(t3))
    graph.getPredecessors(t4).size shouldBe 2

    assert(graph.getGraph(t1).contains(t2))
    graph.getGraph(t1).size shouldBe 1

    assert(graph.getPredecessors(t5).contains(t0))
    assert(graph.getPredecessors(t1).contains(t0))
    assert(!graph.getPredecessors(t2).contains(t0))
  }
}
