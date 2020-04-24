package e2e

import core.Node
import core.applications.Application
import core.applications.Application.Application
import core.data_structures.Transaction
import core.messages.RequestMessage
import core.operations.Operation
import executor.Executor
import orderer.Order
import java.io._
import org.scalatest._
import org.scalatest.matchers.should.Matchers

import scala.collection.{immutable, mutable}

class SimpleDependency extends FlatSpec with Matchers {

  def timecounting(start : Long, end : Long): Unit = {          //function for time calculation & results output
    val duration = (end - start) / 1e9d    //end time counting
    val pw = new FileWriter("SimpleDependency results.txt" ,true)
    //val fw = new FileWriter("test.txt", true)
    pw.write("This test took : "+ duration +" Seconds"+"\n")
    pw.close()
  }


  "One orderer and 3 executor, trying the dependency issue" should "be handled okay" in {

    //============================================================//
    //Initial phase

    val executors = immutable.Seq[String]("ex1","ex2","ex3")
    val orderers = immutable.Seq[String]("ord1")
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    agents.put("ex2", mutable.Set[Application](Application.B))
    agents.put("ex3", mutable.Set[Application](Application.C))
    val ex1 = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, "ex2", executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.C), agents, "ex3", executors, orderers)
    val order = new Order("ord1", "localhost:9091", immutable.Seq[String](), true, executors, orderers)
    val sleepTime = 1000
    val aBalance = 100
    // Set a so a has enough balance for transfers
    var t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),aBalance, Application.A)
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")

    var t2 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"), immutable.Seq[String]("b"),1, Application.B)
    var transactionMessage2 = RequestMessage(t2, 0, "cl1", "ord1")


    //====================================================================//
    //The actually executions inside.
    order.startCkite()
    val time1 = System.nanoTime
    Thread.sleep(sleepTime)

    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)

    client.communication.sendMessage(transactionMessage2)
    Thread.sleep(100)

    for (w <- 2 to 9) {
      val t3 = new Transaction(w, Operation.set, "c", None, immutable.Seq[String](), immutable.Seq[String]("c"),w, Application.C)
      val transactionMessage3 = RequestMessage(t3, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage3)
      Thread.sleep(100)
    }

    Thread.sleep(sleepTime)
    val time2 = System.nanoTime
    order.stopCkite()
    //====================================================================//


    order.currentBlock.getTransactions.length shouldBe 0
    val aRes = 99
    val bRes = 1
    val cRes = 9
    for (ex <- Array(ex1, ex2, ex3)) {
      ex.state.getBalance("a") shouldBe Some(aRes)
      ex.state.getBalance("b") shouldBe Some(bRes)
      ex.state.getBalance("c") shouldBe Some(cRes)
    }

    timecounting(time1,time2)
  }
}
