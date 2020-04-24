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


class ContentionsTest extends FlatSpec with Matchers {
  // This parts main decrease the influence by the networks, but in our test cases, the interconnect delay can be igonred.
  // The test results even suffers from a cold start

  //Parameters here
  val Executornumber = 5
  val Ordernumber = 1
  val exe = "ex"
  val ord = "ord"
  val sleepTime = 1000
  val Balance = 100
  val Executionsnumber = 6
  val Number_of_transfers = 3


  def timecounting(start : Long, end : Long, patten : Int): Unit = {          //function for time calculation & results output
    val duration = (end - start) / 1e9d    //end time counting
    val pw = new FileWriter("Contentions test.txt" ,true)
    //val fw = new FileWriter("test.txt", true)
    pw.write("The patten: " + patten + " test took : "+ duration +" Seconds" + "Total executions number:" + (Executionsnumber * 3 + 2) + "\n")
    pw.close()
  }


  "Contention test patten 1 a->b, c->d, c->e, One order" should "be handled okay" in {

    //============================================================//
    //Initial phase

    //Initial phase, setting up the node, orders,excutors and clients

    //val cl = "cl"

    var executors = immutable.Seq[String]()
    var orderers = immutable.Seq[String]()
    for(i <- 1 to  Executornumber){
      executors :+= (exe + i.toString)
    }
    for(i <- 1 to  Ordernumber){
      orderers :+= (ord + i.toString)
    }
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()

    agents.put(executors.head, mutable.Set[Application](Application.A))
    agents.put(executors(1), mutable.Set[Application](Application.B))
    agents.put(executors(2), mutable.Set[Application](Application.C))
    agents.put(executors(3), mutable.Set[Application](Application.D))
    agents.put(executors(4), mutable.Set[Application](Application.E))


    val ex1 = new Executor(mutable.Set(Application.A), agents, executors.head, executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, executors(1), executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.C), agents, executors(2), executors, orderers)
    val ex4 = new Executor(mutable.Set(Application.D), agents, executors(3), executors, orderers)
    val ex5 = new Executor(mutable.Set(Application.E), agents, executors(4), executors, orderers)

    val order = new Order("ord1", "localhost:9091", immutable.Seq[String](), true, executors, orderers)

    // Set a so a has enough balance for transfers
    var t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),Balance, Application.A)
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")

    val t3 = new Transaction(0, Operation.set, "c", None, immutable.Seq[String](), immutable.Seq[String]("c"),Balance, Application.C)
    val transactionMessage3 = RequestMessage(t3, 0, "cl1", "ord1")


    val time1 = System.nanoTime
    //====================================================================//
    //The actually executions inside.
    order.startCkite()
    Thread.sleep(sleepTime)

    //set up the balance in a and c account
    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)
    client.communication.sendMessage(transactionMessage3)
    Thread.sleep(100)


    //performing the transfer

    for(i <- 1 to Executionsnumber){
      var t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a", "b"), immutable.Seq[String]("a", "b"),1, Application.B)
      var transactionMessage2 = RequestMessage(t2, i, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)

      var t4 = new Transaction(i, Operation.transfer, "c", Some("d"), immutable.Seq[String]("c", "d"), immutable.Seq[String]("c", "d"),1, Application.D)
      var transactionMessage4 = RequestMessage(t4, i, "cl1", "ord1")

      var t5 = new Transaction(i, Operation.transfer, "c", Some("e"), immutable.Seq[String]("c", "e"), immutable.Seq[String]("c", "e"),1, Application.E)
      var transactionMessage5 = RequestMessage(t5, i, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage4)
      Thread.sleep(100)

      client.communication.sendMessage(transactionMessage5)
      Thread.sleep(100)
    }




    Thread.sleep(sleepTime)
    order.stopCkite()
    Thread.sleep(sleepTime)
    //====================================================================//
    val time2 = System.nanoTime

    order.currentBlock.getTransactions.length shouldBe 0
    val aRes = 100-Executionsnumber
    val bRes = Executionsnumber
    val cRes = 100-(2 * Executionsnumber)
    val dRes = Executionsnumber
    val eRes = Executionsnumber
    for (ex <- Array(ex1, ex2, ex3, ex4, ex5)) {
      ex.state.getBalance("a") shouldBe Some(aRes)
      ex.state.getBalance("b") shouldBe Some(bRes)
      ex.state.getBalance("c") shouldBe Some(cRes)
      ex.state.getBalance("d") shouldBe Some(dRes)
      ex.state.getBalance("e") shouldBe Some(eRes)
    }

    timecounting(time1,time2,1)
  }

  "Contention test patten 2 a -> b -> c -> d -> e, One order" should "be handled okay" in {

    //============================================================//
    //Initial phase

    //Initial phase, setting up the node, orders,excutors and clients

    //val cl = "cl"

    var executors = immutable.Seq[String]()
    var orderers = immutable.Seq[String]()
    for(i <- 1 to  Executornumber){
      executors :+= (exe + i.toString)
    }
    for(i <- 1 to  Ordernumber){
      orderers :+= (ord + i.toString)
    }
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()

    agents.put(executors.head, mutable.Set[Application](Application.A))
    agents.put(executors(1), mutable.Set[Application](Application.B))
    agents.put(executors(2), mutable.Set[Application](Application.C))
    agents.put(executors(3), mutable.Set[Application](Application.D))
    agents.put(executors(4), mutable.Set[Application](Application.E))


    val ex1 = new Executor(mutable.Set(Application.A), agents, executors.head, executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, executors(1), executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.C), agents, executors(2), executors, orderers)
    val ex4 = new Executor(mutable.Set(Application.D), agents, executors(3), executors, orderers)
    val ex5 = new Executor(mutable.Set(Application.E), agents, executors(4), executors, orderers)

    val order = new Order("ord1", "localhost:9091", immutable.Seq[String](), true, executors, orderers)

    // Set a so a has enough balance for transfers
    var t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),Balance, Application.A)
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")

    val t3 = new Transaction(0, Operation.set, "c", None, immutable.Seq[String](), immutable.Seq[String]("c"),Balance, Application.C)
    val transactionMessage3 = RequestMessage(t3, 0, "cl1", "ord1")


    val time1 = System.nanoTime
    //====================================================================//
    //The actually executions inside.
    order.startCkite()
    Thread.sleep(sleepTime)

    //set up the balance in a and c account
    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)
    client.communication.sendMessage(transactionMessage3)
    Thread.sleep(100)


    //performing the transfer

    for(i <- 1 to Executionsnumber){
      val t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"), immutable.Seq[String]("b"),1, Application.B)
      val transactionMessage2 = RequestMessage(t2, 0, "cl1", "ord1")

      val t4 = new Transaction(i, Operation.transfer, "c", Some("d"), immutable.Seq[String]("c"), immutable.Seq[String]("d"),1, Application.D)
      val transactionMessage4 = RequestMessage(t4, 0, "cl1", "ord1")

      val t5 = new Transaction(i, Operation.transfer, "d", Some("e"), immutable.Seq[String]("d"), immutable.Seq[String]("e"),1, Application.E)
      val transactionMessage5 = RequestMessage(t5, 0, "cl1", "ord1")

      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)

      client.communication.sendMessage(transactionMessage4)
      Thread.sleep(100)

      client.communication.sendMessage(transactionMessage5)
      Thread.sleep(100)
    }
    Thread.sleep(sleepTime)
    order.stopCkite()
    //====================================================================//
    val time2 = System.nanoTime

    order.currentBlock.getTransactions.length shouldBe 0
    val aRes = Balance-Executionsnumber
    val bRes = Executionsnumber
    val cRes = Balance-Executionsnumber
    val dRes = 0
    val eRes = Executionsnumber
    for (ex <- Array(ex1, ex2, ex3, ex4, ex5)) {
      ex.state.getBalance("a") shouldBe Some(aRes)
      ex.state.getBalance("b") shouldBe Some(bRes)
      ex.state.getBalance("c") shouldBe Some(cRes)
      ex.state.getBalance("d") shouldBe Some(dRes)
      ex.state.getBalance("e") shouldBe Some(eRes)
    }

    timecounting(time1,time2,2)
  }




}
