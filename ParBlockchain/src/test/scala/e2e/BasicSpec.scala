package e2e

import core.Node
import core.applications.Application
import core.applications.Application.Application
import core.data_structures.Transaction
import core.messages.RequestMessage
import core.operations.Operation
import executor.Executor
import orderer.Order
import org.scalatest._
import org.scalatest.matchers.should.Matchers

import scala.collection.{immutable, mutable}

class BasicSpec extends FlatSpec with Matchers {
  "One orderer and one executor" should "be handled okay" in {
    val executors = immutable.Seq[String]("ex1")
    val orderers = immutable.Seq[String]("ord1")
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application]())
    val ex = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val order = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9091"), true, executors, orderers)
    order.startCkite()
    val sleepTime = 1000
    Thread.sleep(sleepTime)

    for (w <- 0 to 9) {
      val t = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage = RequestMessage(t, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage)
      Thread.sleep(100)
    }
    Thread.sleep(4 * sleepTime)
    order.stopCkite()
    order.currentBlock.getTransactions.length shouldBe 0
    val res = 9
    ex.state.getBalance("a") shouldBe Some(res)
  }

  "One orderer and one executor" should "handle two blocks with max messages" in {
    val executors = immutable.Seq[String]("ex1")
    val orderers = immutable.Seq[String]("ord1")
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    val ex = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val order = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9091"), true, executors, orderers)
    order.startCkite()
    val sleepTime = 1500
    Thread.sleep(sleepTime)

    for (w <- 0 to 19) {
      val t = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage = RequestMessage(t, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage)
      Thread.sleep(100)
    }
    Thread.sleep(2 * sleepTime)
    order.stopCkite()
    order.currentBlock.getTransactions.length shouldBe 0
    val res = 19
    ex.state.getBalance("a") shouldBe Some(res)
  }

  "One orderer and one executor" should "handle a single block cut due to timeout" in {
    val executors = immutable.Seq[String]("ex1")
    val orderers = immutable.Seq[String]("ord1")
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application]())
    val ex = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val order = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9091"), true, executors, orderers)
    order.startCkite()
    val sleepTime = 1000
    Thread.sleep(sleepTime)

    val t = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),1, Application.A)
    val transactionMessage = RequestMessage(t, 0, "cl1", "ord1")
    client.communication.sendMessage(transactionMessage)

    Thread.sleep(sleepTime)
    order.initCutBlock()

    Thread.sleep(3 *sleepTime)
    order.stopCkite()
    order.currentBlock.getTransactions.length shouldBe 0

    ex.state.getBalance("a") shouldBe Some(1)
  }

  "2 Orderers and 3 Executors with 3 Applications" should "function properly" in {
    val executors = immutable.Seq[String]("ex1", "ex2", "ex3")
    val orderers = immutable.Seq[String]("ord1", "ord2")

    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    agents.put("ex2", mutable.Set[Application](Application.B))
    agents.put("ex3", mutable.Set[Application](Application.C))
    val ex1 = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, "ex2", executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.C), agents, "ex3", executors, orderers)

    val ord1 = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9092"),
      true, executors, orderers)
    val ord2 = new Order("ord2", "localhost:9092", immutable.Seq[String]("localhost:9091"),
      false, executors, orderers)

    ord1.startCkite()
    ord2.startCkite()

    val sleepTime = 1000
    Thread.sleep(sleepTime)

    for (w <- 0 to 9) {
      val t = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage = RequestMessage(t, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage)
      Thread.sleep(100)
    }

    Thread.sleep(4 * sleepTime)
    ord1.stopCkite()
    ord2.stopCkite()
    ord1.currentBlock.getTransactions.length shouldBe 0
    ord2.currentBlock.getTransactions.length shouldBe 0

    val res = 9
    ex1.state.getBalance("a") shouldBe Some(res)
    ex2.state.getBalance("a") shouldBe Some(res)
    ex3.state.getBalance("a") shouldBe Some(res)
  }

  "One Order and two executors with one applications" should "function properly" in {
    val t1 = System.nanoTime    //start time counting

    val executors = immutable.Seq[String]("ex1", "ex2")
    val orderers = immutable.Seq[String]("ord1")

    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    agents.put("ex2", mutable.Set[Application](Application.B))
    val ex1 = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, "ex2", executors, orderers)


    val ord1 = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9092"),
      true, executors, orderers)

    ord1.startCkite()

    val sleepTime = 1000
    Thread.sleep(sleepTime)

    for (w <- 0 to 9) {
      val t = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage = RequestMessage(t, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage)
      Thread.sleep(100)
    }

    Thread.sleep(2 * sleepTime)

    ord1.stopCkite()

    ord1.currentBlock.getTransactions.length shouldBe 0

    val res = 9       //this one is mismatched
    ex1.state.getBalance("a") shouldBe Some(res)
    ex2.state.getBalance("a") shouldBe Some(res)


    val duration = (System.nanoTime - t1) / 1e9d    //end time counting
    System.err.println("Run time of this test : " + duration);     //print in the error part

  }


}
