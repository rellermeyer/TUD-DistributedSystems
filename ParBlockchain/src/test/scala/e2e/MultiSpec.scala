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

class MultiSpec extends FlatSpec with Matchers {

  "Two transactions, 1 order, 2 executor for different applications" should "function properly" in {
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
      val t1 = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage1)
      Thread.sleep(100)
    }

    Thread.sleep(sleepTime)   //time for propagation this first transactions

    for (w <- 0 to 9){
      val t2 = new Transaction(w, Operation.set, "b", None, immutable.Seq[String](), immutable.Seq[String]("b"),w, Application.B)
      val transactionMessage2 = RequestMessage(t2, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)
    }

    Thread.sleep(2*sleepTime)

    ord1.currentBlock.getTransactions.length shouldBe 0

    ord1.stopCkite()
    Thread.sleep(sleepTime)

    val res = 9       //this one is mismatched
    ex1.state.getBalance("a") shouldBe Some(res)
    ex1.state.getBalance("b") shouldBe Some(res)
    ex2.state.getBalance("a") shouldBe Some(res)
    ex2.state.getBalance("b") shouldBe Some(res)

    val duration = (System.nanoTime - t1) / 1e9d    //end time counting
    System.err.println("Run time of this test : " + duration);     //print in the error part
    //Run time of this test : 4.093353652
  }

  "Two transactions, 2 order, 3 executor for different applications" should "function properly" in {
    val t1 = System.nanoTime    //start time counting

    val executors = immutable.Seq[String]("ex1", "ex2","ex3")
    val orderers = immutable.Seq[String]("ord1","ord2")

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
      val t1 = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage1)
      Thread.sleep(100)
    }

    Thread.sleep(2*sleepTime)

    for (w <- 0 to 9){
      val t2 = new Transaction(w, Operation.set, "b", None, immutable.Seq[String](), immutable.Seq[String]("b"),w, Application.B)
      val transactionMessage2 = RequestMessage(t2, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)
    }

    Thread.sleep(4*sleepTime)

    ord1.stopCkite()
    ord2.stopCkite()
    Thread.sleep(sleepTime)

    ord1.currentBlock.getTransactions.length shouldBe 0
    ord2.currentBlock.getTransactions.length shouldBe 0

    val res = 9       //this one is mismatched
    ex1.state.getBalance("a") shouldBe Some(res)
    ex2.state.getBalance("b") shouldBe Some(res)
    ex3.state.getBalance("a") shouldBe Some(res)
    ex3.state.getBalance("b") shouldBe Some(res)

    val duration = (System.nanoTime - t1) / 1e9d    //end time counting
    System.err.println("Run time of this test : " + duration);     //print in the error part
    //Run time of this test : 7.796634759
  }

  "2 clients sending 2 transactions to 1 orders and distribute to two executors" should "function properly" in {
    val t1 = System.nanoTime    //start time counting

    val executors = immutable.Seq[String]("ex1","ex2")
    val orderers = immutable.Seq[String]("ord1","ord2")


    val client1 = new Node("cl1", executors, orderers)               //client can not added due akka problem.
    val client2 = new Node("cl2", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    agents.put("ex2", mutable.Set[Application](Application.B))
    val ex1 = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, "ex2", executors, orderers)


    val ord1 = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9092"),
      true, executors, orderers)
    val ord2 = new Order("ord2", "localhost:9092", immutable.Seq[String]("localhost:9091"),
      true, executors, orderers)

    ord1.startCkite()
    ord2.startCkite()

    val sleepTime = 1000
    Thread.sleep(sleepTime)

    for (w <- 0 to 9) {
      val t1 = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")
      client1.communication.sendMessage(transactionMessage1)
      Thread.sleep(100)
    }

    Thread.sleep(sleepTime)

    for (w <- 0 to 9){
      val t2 = new Transaction(w, Operation.set, "b", None, immutable.Seq[String](), immutable.Seq[String]("b"),w, Application.B)
      val transactionMessage2 = RequestMessage(t2, 0, "cl2", "ord1")
      client2.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)
    }

    Thread.sleep(sleepTime)

    ord1.stopCkite()
    ord2.stopCkite()
    Thread.sleep(sleepTime)

    ord1.currentBlock.getTransactions.length shouldBe 0
    ord2.currentBlock.getTransactions.length shouldBe 0

    val res = 9       //this one is mismatched
    ex1.state.getBalance("a") shouldBe Some(res)
    ex1.state.getBalance("b") shouldBe Some(res)
    ex2.state.getBalance("a") shouldBe Some(res)
    ex2.state.getBalance("b") shouldBe Some(res)

    val duration = (System.nanoTime - t1) / 1e9d    //end time counting
    System.err.println("Run time of this test : " + duration);     //print in the error part
    //Run time of this test : 6.622273616
  }

  "2 clients sending 2 transactions to 2 different orders and distribute to two executors" should "function properly" in {
    val t1 = System.nanoTime    //start time counting

    val executors = immutable.Seq[String]("ex1","ex2")
    val orderers = immutable.Seq[String]("ord1","ord2")


    val client1 = new Node("cl1", executors, orderers)               //client can not added due akka problem.
    val client2 = new Node("cl2", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    agents.put("ex2", mutable.Set[Application](Application.B))
    val ex1 = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, "ex2", executors, orderers)


    val ord1 = new Order("ord1", "localhost:9091", immutable.Seq[String]("localhost:9092"),
      true, executors, orderers)
    val ord2 = new Order("ord2", "localhost:9092", immutable.Seq[String]("localhost:9091"),
      true, executors, orderers)

    ord1.startCkite()
    ord2.startCkite()

    val sleepTime = 1000
    Thread.sleep(sleepTime)

    for (w <- 0 to 9) {
      val t1 = new Transaction(w, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),w, Application.A)
      val transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")
      client1.communication.sendMessage(transactionMessage1)
      Thread.sleep(100)
    }

    Thread.sleep(sleepTime)

    for (w <- 0 to 9){
      val t2 = new Transaction(w, Operation.set, "b", None, immutable.Seq[String](), immutable.Seq[String]("b"),w, Application.B)
      val transactionMessage2 = RequestMessage(t2, 0, "cl2", "ord2")
      client2.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)
    }

    Thread.sleep(sleepTime)

    ord1.stopCkite()
    ord2.stopCkite()
    Thread.sleep(sleepTime)

    ord1.currentBlock.getTransactions.length shouldBe 0
    ord2.currentBlock.getTransactions.length shouldBe 0

    val res = 9       //this one is mismatched
    ex1.state.getBalance("a") shouldBe Some(res)
    ex1.state.getBalance("b") shouldBe None             //the using of two clients will distributed to two threads, this one only linked to the first client
    ex2.state.getBalance("a") shouldBe Some(res)
    ex2.state.getBalance("b") shouldBe None

    val duration = (System.nanoTime - t1) / 1e9d    //end time counting
    System.err.println("Run time of this test : " + duration);     //print in the error part
    //Run time of this test : 6.489627094
  }


}
/*
[info] MultipleSpec:
[info] Two transactions, 1 order, 2 executor for different applications
[info] - should function properly
runtime: 4.09
4.58 4.88
4.52 4.15
[info] Two transactions, 2 order, 3 executor for different applications
[info] - should function properly
runtime: 7.79
7.27 7.70
7.64 8.04
[info] 2 clients sending 2 transactions to 1 orders and distribute to two executors
[info] - should function properly
runtime: 6.62
5.69 6.86
5.39 5.92
[info] 2 clients sending 2 transactions to 2 different orders and distribute to two executors
[info] - should function properly
runtime: 6.48
6.31 6.19
5.93 6.34
 */