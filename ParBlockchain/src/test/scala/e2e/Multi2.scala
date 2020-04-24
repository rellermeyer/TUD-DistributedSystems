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

/*

File created for testing the performance when adding more executor for one/multiple transactions.

 */

case class Multi2() extends FlatSpec with Matchers {


  val TransactionNumber = 100 // The number which the loop counts ie. the transactions inside the system, 10000 is too high, don't try this,cost more than 5 min

  def timecounting(start: Long, end: Long, executors: Int, orders: Int, transactions: Int, dependen : Int): Unit = { //function for time calculation & results output
    val duration = (end - start) / 1e9d //end time counting
    val pw = new FileWriter("Executor parallel test.txt", true)
    pw.write("Transactions :" + transactions + " with " + executors + " executors, " + orders +
      "orders, test took : " + duration + " Seconds" + dependen +  " dependency" + "\n")
    pw.close()
  }


  "Test for one orderer 7 executor, different applications + dependency" should "test past" in {

    //============================================================//
    //Parameters could be changed
    //    val TransactionNumber = 1000         // The number which the loop counts ie. the transactions inside the system, 10000 is too high, don't try this,cost more than 5 min
    val Executornumber = 7
    val Ordernumber = 1
    val sleepTime = 1000
    val transferamount = 1 //This amount got some problem here

    //Initial phase, setting up the node, orders,excutors and clients
    val exe = "ex"
    val ord = "ord"
    //val cl = "cl"

    var executors = immutable.Seq[String]()
    var orderers = immutable.Seq[String]()
    for (i <- 1 to Executornumber) {
      executors :+= (exe + i.toString)
    }
    for (i <- 1 to Ordernumber) {
      orderers :+= (ord + i.toString)
    }

    val client = new Node("cl1", executors, orderers) //clients limits to be local
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put(executors.head, mutable.Set[Application](Application.A))
    agents.put(executors.head, mutable.Set[Application](Application.B))
    agents.put(executors.head, mutable.Set[Application](Application.C))
    agents.put(executors.head, mutable.Set[Application](Application.D))
    agents.put(executors.head, mutable.Set[Application](Application.E))
    agents.put(executors.head, mutable.Set[Application](Application.F))
    agents.put(executors.head, mutable.Set[Application](Application.G))

    //  for(i <- 2 to  Executornumber){
    //  agents.put(executors(i-1), mutable.Set[Application](Application.B))
    //}

    // The assignment of this executor is hard to be put inside a loop i think
    val ex1 = new Executor(mutable.Set(Application.A), agents, executors.head, executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, executors(1), executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.C), agents, executors(2), executors, orderers)
    val ex4 = new Executor(mutable.Set(Application.D), agents, executors(3), executors, orderers)
    val ex5 = new Executor(mutable.Set(Application.E), agents, executors(4), executors, orderers)
    val ex6 = new Executor(mutable.Set(Application.F), agents, executors(5), executors, orderers)
    val ex7 = new Executor(mutable.Set(Application.G), agents, executors(6), executors, orderers)

    val ord1 = new Order(orderers.head, "localhost:9091", immutable.Seq[String](),
      true, executors, orderers)
    //  val ord2 = new Order(orderers(1), "localhost:9092", immutable.Seq[String]("localhost:9091","localhost:9093"),
    //  false, executors, orderers)
    //  val ord3 = new Order(orderers(2), "localhost:9093", immutable.Seq[String]("localhost:9091","localhost:9092"),
    //  false, executors, orderers)


    //====================================================================//
    //Start of ckite
    ord1.startCkite()
    //  ord2.startCkite()
    //  ord3.startCkite()
    Thread.sleep(sleepTime)

    //===================================================================//
    //Execution phase to be measured
    val time1 = System.nanoTime


    //Setting up transactions
    val t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](),
      immutable.Seq[String]("a"), 15000, Application.A) //give a enough balance
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", orderers.head)

    val t5 = new Transaction(0, Operation.set, "e", None, immutable.Seq[String](),
      immutable.Seq[String]("a"), 15000, Application.A) //give a enough balance
    var transactionMessage5 = RequestMessage(t5, 0, "cl1", orderers.head)

    //Transfers
    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)

    client.communication.sendMessage(transactionMessage5)
    Thread.sleep(100)

    for (i <- 1 to (TransactionNumber/6) by 1) {
      val t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a", "b"), transferamount * 10, Application.B)
      var transactionMessage2 = RequestMessage(t2, 0, "cl1", orderers.head)
      val t3 = new Transaction(i,Operation.transfer, "b", Some("c"), immutable.Seq[String]("b"),
        immutable.Seq[String]("b", "c"), transferamount * 10, Application.C)
      var transactionMessage3 = RequestMessage(t3, 0, "cl1", orderers.head)
      val t4 = new Transaction(i, Operation.transfer, "c", Some("d"), immutable.Seq[String]("c"),
        immutable.Seq[String]("c", "d"), transferamount * 10, Application.D)
      var transactionMessage4 = RequestMessage(t4, 0, "cl1", orderers.head)
      val t6 = new Transaction(i, Operation.transfer, "e", Some("f"), immutable.Seq[String]("e"),
        immutable.Seq[String]("e", "f"), transferamount * 10, Application.E)
      var transactionMessage6 = RequestMessage(t6, 0, "cl1", orderers.head)
      val t7 = new Transaction(i, Operation.transfer, "f", Some("g"), immutable.Seq[String]("f"),
        immutable.Seq[String]("f", "g"), transferamount * 10, Application.F)
      var transactionMessage7 = RequestMessage(t7, 0, "cl1", orderers.head)
      val t8 = new Transaction(i, Operation.transfer, "g", Some("h"), immutable.Seq[String]("g"),
        immutable.Seq[String]("g", "h"), transferamount * 10, Application.G)
      var transactionMessage8 = RequestMessage(t8, 0, "cl1", orderers.head)

      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage3)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage4)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage6)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage7)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage8)
      Thread.sleep(80) //100 seems not enough here
    }

    Thread.sleep(3 * sleepTime)

    //====================================================================//
    //End of execution time
    val time2 = System.nanoTime

    //end of ckite
    ord1.stopCkite()
    Thread.sleep(sleepTime)
    //  ord2.stopCkite()
    //  ord3.stopCkite()
    //====================================================================//


    //ord1.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
    //ord2.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
    //ord3.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
    //    val aRes = 5000
    //    val bRes = 10000
    //    for (ex <- Array(ex1, ex2, ex3,ex4 )) {
    //      ex.state.getBalance("a") shouldBe Some(aRes)
    //      ex.state.getBalance("b") shouldBe Some(bRes)
    //    }

    timecounting(time1, time2, Executornumber, Ordernumber, TransactionNumber, 1)
  }

  "Test for 2 orderer 7 executor, different applications + dependency" should "test past" in {

    //============================================================//
    //Parameters could be changed
    //    val TransactionNumber = 1000         // The number which the loop counts ie. the transactions inside the system, 10000 is too high, don't try this,cost more than 5 min
    val Executornumber = 7
    val Ordernumber = 2
    val sleepTime = 1000
    val transferamount = 1 //This amount got some problem here

    //Initial phase, setting up the node, orders,excutors and clients
    val exe = "ex"
    val ord = "ord"
    //val cl = "cl"

    var executors = immutable.Seq[String]()
    var orderers = immutable.Seq[String]()
    for (i <- 1 to Executornumber) {
      executors :+= (exe + i.toString)
    }
    for (i <- 1 to Ordernumber) {
      orderers :+= (ord + i.toString)
    }

    val client = new Node("cl1", executors, orderers) //clients limits to be local
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put(executors.head, mutable.Set[Application](Application.A))
    agents.put(executors.head, mutable.Set[Application](Application.B))
    agents.put(executors.head, mutable.Set[Application](Application.C))
    agents.put(executors.head, mutable.Set[Application](Application.D))
    agents.put(executors.head, mutable.Set[Application](Application.E))
    agents.put(executors.head, mutable.Set[Application](Application.F))
    agents.put(executors.head, mutable.Set[Application](Application.G))

    //  for(i <- 2 to  Executornumber){
    //  agents.put(executors(i-1), mutable.Set[Application](Application.B))
    //}

    // The assignment of this executor is hard to be put inside a loop i think
    val ex1 = new Executor(mutable.Set(Application.A), agents, executors.head, executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, executors(1), executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.C), agents, executors(2), executors, orderers)
    val ex4 = new Executor(mutable.Set(Application.D), agents, executors(3), executors, orderers)
    val ex5 = new Executor(mutable.Set(Application.E), agents, executors(4), executors, orderers)
    val ex6 = new Executor(mutable.Set(Application.F), agents, executors(5), executors, orderers)
    val ex7 = new Executor(mutable.Set(Application.G), agents, executors(6), executors, orderers)

    val ord1 = new Order(orderers.head, "localhost:9091", immutable.Seq[String](),
      true, executors, orderers)
    val ord2 = new Order(orderers(1), "localhost:9092", immutable.Seq[String]("localhost:9091"),
      false, executors, orderers)
    //  val ord3 = new Order(orderers(2), "localhost:9093", immutable.Seq[String]("localhost:9091","localhost:9092"),
    //  false, executors, orderers)


    //====================================================================//
    //Start of ckite
    ord1.startCkite()
    Thread.sleep(sleepTime)
    ord2.startCkite()
    //  ord3.startCkite()
    Thread.sleep(sleepTime)

    //===================================================================//
    //Execution phase to be measured
    val time1 = System.nanoTime


    //Setting up transactions
    val t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"), 15000, Application.A) //give a enough balance
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", orderers.head)

    val t5 = new Transaction(0, Operation.set, "e", None, immutable.Seq[String](), immutable.Seq[String]("a"), 15000, Application.A) //give a enough balance
    var transactionMessage5 = RequestMessage(t5, 0, "cl1", orderers(1))

    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)

    client.communication.sendMessage(transactionMessage5)
    Thread.sleep(100)

    for (i <- 1 to (TransactionNumber/6) by 1) {
      val t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a", "b"), transferamount * 10, Application.B)
      var transactionMessage2 = RequestMessage(t2, 0, "cl1", orderers.head)
      val t3 = new Transaction(i, Operation.transfer, "b", Some("c"), immutable.Seq[String]("b"),
        immutable.Seq[String]("b", "c"), transferamount * 10, Application.C)
      var transactionMessage3 = RequestMessage(t3, 0, "cl1", orderers.head)
      val t4 = new Transaction(i, Operation.transfer, "c", Some("d"), immutable.Seq[String]("c"),
        immutable.Seq[String]("c", "d"), transferamount * 10, Application.D)
      var transactionMessage4 = RequestMessage(t4, 0, "cl1", orderers.head)
      val t6 = new Transaction(i ,Operation.transfer, "e", Some("f"), immutable.Seq[String]("e"),
        immutable.Seq[String]("e", "f"), transferamount * 10, Application.E)
      var transactionMessage6 = RequestMessage(t6, 0, "cl1", orderers(1))
      val t7 = new Transaction(i, Operation.transfer, "f", Some("g"), immutable.Seq[String]("f"),
        immutable.Seq[String]("f", "g"), transferamount * 10, Application.F)
      var transactionMessage7 = RequestMessage(t7, 0, "cl1", orderers(1))
      val t8 = new Transaction(i, Operation.transfer, "g", Some("h"), immutable.Seq[String]("g"),
        immutable.Seq[String]("g", "h"), transferamount * 10, Application.G)
      var transactionMessage8 = RequestMessage(t8, 0, "cl1", orderers(1))

      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage3)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage4)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage6)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage7)
      Thread.sleep(80) //100 seems not enough here
      client.communication.sendMessage(transactionMessage8)
      Thread.sleep(80) //100 seems not enough here
    }

    Thread.sleep(3 * sleepTime)

    //====================================================================//
    //End of execution time
    val time2 = System.nanoTime

    //end of ckite
    ord1.stopCkite()
    ord2.stopCkite()
    Thread.sleep(sleepTime)
    //  ord3.stopCkite()
    //====================================================================//


    //ord1.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
    //ord2.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
    //ord3.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
    //    val aRes = 5000
    //    val bRes = 10000
    //    for (ex <- Array(ex1, ex2, ex3,ex4 )) {
    //      ex.state.getBalance("a") shouldBe Some(aRes)
    //      ex.state.getBalance("b") shouldBe Some(bRes)
    //    }

    timecounting(time1, time2, Executornumber, Ordernumber, TransactionNumber, 1)
  }


}
