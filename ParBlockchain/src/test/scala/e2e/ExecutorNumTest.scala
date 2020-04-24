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

case class ExecutorNumTest() extends FlatSpec with Matchers {

  val TransactionNumber = 500         // The number which the loop counts ie. the transactions inside the system, 10000 is too high, don't try this,cost more than 5 min

  def timecounting(start : Long, end : Long, executors : Int, orders : Int, transactions : Int): Unit = {          //function for time calculation & results output
    val duration = (end - start) / 1e9d    //end time counting
    val pw = new FileWriter("Number of executor test.txt" ,true)
    pw.write("Transactions :" + transactions + " with " + executors + " executors, " + orders +
      "orders, test took : " + duration + " Seconds" + "\n" )
    pw.close()
  }

  "Test for test multiple executors/orders for the system " should "test past" in {

    //============================================================//
    //Parameters could be changed
//    val TransactionNumber = 1000         // The number which the loop counts ie. the transactions inside the system, 10000 is too high, don't try this,cost more than 5 min
    val Executornumber = 4
    val Ordernumber = 3
    val sleepTime = 1000
    val transferamount = 1         //This amount got some problem here

    //Initial phase, setting up the node, orders,excutors and clients
    val exe = "ex"
    val ord = "ord"
    //val cl = "cl"

    var executors = immutable.Seq[String]()
    var orderers = immutable.Seq[String]()
    for(i <- 1 to  Executornumber){
      executors :+= (exe + i.toString)
    }
    for(i <- 1 to  Ordernumber){
      orderers :+= (ord + i.toString)
    }

    val client = new Node("cl1", executors, orderers)           //clients limits to be local
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put(executors.head, mutable.Set[Application](Application.A))

    for(i <- 2 to  Executornumber){
      agents.put(executors(i-1), mutable.Set[Application](Application.B))
    }

    // The assignment of this executor is hard to be put inside a loop i think
    val ex1 = new Executor(mutable.Set(Application.A), agents, executors.head, executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, executors(1), executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.B), agents, executors(2), executors, orderers)
    val ex4 = new Executor(mutable.Set(Application.B), agents, executors(3), executors, orderers)

    val ord1 = new Order(orderers.head, "localhost:9091", immutable.Seq[String]("localhost:9092","localhost:9093"),
      true, executors, orderers)
    val ord2 = new Order(orderers(1), "localhost:9092", immutable.Seq[String]("localhost:9091","localhost:9093"),
      false, executors, orderers)
    val ord3 = new Order(orderers(2), "localhost:9093", immutable.Seq[String]("localhost:9091","localhost:9092"),
      false, executors, orderers)



    //====================================================================//
    //Start of ckite
    ord1.startCkite()
    ord2.startCkite()
    ord3.startCkite()
    Thread.sleep(sleepTime)

    //===================================================================//
    //Execution phase to be measured
    val time1 = System.nanoTime


    //Setting up transactions
    val t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](),
      immutable.Seq[String]("a"),15000, Application.A)
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")

    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)

    for (i <- 1 to (TransactionNumber/Ordernumber) by 1){
      val t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage2 = RequestMessage(t2, 0, "cl1", "ord1")
      val t3 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage3 = RequestMessage(t3, 0, "cl1", "ord2")
      val t4 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage4 = RequestMessage(t4, 0, "cl1", "ord3")

      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(100)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage3)
      Thread.sleep(100)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage4)
      Thread.sleep(100)    //100 seems not enough here
    }

    Thread.sleep(3*sleepTime)

    //====================================================================//
    //End of execution time
    val time2 = System.nanoTime

    //end of ckite
    ord1.stopCkite()
    ord2.stopCkite()
    ord3.stopCkite()
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

    timecounting(time1,time2,Executornumber,Ordernumber,TransactionNumber)
  }

  "Test for test multiple executors/orders for the system patten 2 " should "test past" in {

    //============================================================//
    //Parameters could be changed
//    val TransactionNumber = 1000         // The number which the loop counts ie. the transactions inside the system, 10000 is too high, don't try this,cost more than 5 min
    val Executornumber = 7
    val Ordernumber = 6
    val sleepTime = 1000
    val transferamount = 1         //This amount got some problem here

    //Initial phase, setting up the node, orders,excutors and clients
    val exe = "ex"
    val ord = "ord"
    //val cl = "cl"

    var executors = immutable.Seq[String]()
    var orderers = immutable.Seq[String]()
    for(i <- 1 to  Executornumber){
      executors :+= (exe + i.toString)
    }
    for(i <- 1 to  Ordernumber){
      orderers :+= (ord + i.toString)
    }

    val client = new Node("cl1", executors, orderers)           //clients limits to be local
    val client2 = new Node("cl2", executors, orderers)           //clients limits to be local
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put(executors.head, mutable.Set[Application](Application.A))

    for(i <- 2 to  Executornumber){
      agents.put(executors(i-1), mutable.Set[Application](Application.B))
    }

    // The assignment of this executor is hard to be put inside a loop i think
    val ex1 = new Executor(mutable.Set(Application.A), agents, executors.head, executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, executors(1), executors, orderers)
    val ex3 = new Executor(mutable.Set(Application.B), agents, executors(2), executors, orderers)
    val ex4 = new Executor(mutable.Set(Application.B), agents, executors(3), executors, orderers)
    val ex5 = new Executor(mutable.Set(Application.B), agents, executors(3), executors, orderers)
    val ex6 = new Executor(mutable.Set(Application.B), agents, executors(3), executors, orderers)
    val ex7 = new Executor(mutable.Set(Application.B), agents, executors(3), executors, orderers)

    val ord1 = new Order(orderers.head, "localhost:9091", immutable.Seq[String]("localhost:9091","localhost:9092","localhost:9093","localhost:9094","localhost:9097","localhost:9096"),
      true, executors, orderers)
    val ord2 = new Order(orderers(1), "localhost:9092", immutable.Seq[String]("localhost:9091","localhost:9092","localhost:9093","localhost:9094","localhost:9097","localhost:9096"),
      false, executors, orderers)
    val ord3 = new Order(orderers(2), "localhost:9093", immutable.Seq[String]("localhost:9091","localhost:9092","localhost:9093","localhost:9094","localhost:9097","localhost:9096"),
      false, executors, orderers)
    val ord4 = new Order(orderers(3), "localhost:9094", immutable.Seq[String]("localhost:9091","localhost:9092","localhost:9093","localhost:9094","localhost:9097","localhost:9096"),
      false, executors, orderers)
    val ord5 = new Order(orderers(4), "localhost:9095", immutable.Seq[String]("localhost:9091","localhost:9092","localhost:9093","localhost:9094","localhost:9097","localhost:9096"),
      false, executors, orderers)
    val ord6 = new Order(orderers(5), "localhost:9096", immutable.Seq[String]("localhost:9091","localhost:9092","localhost:9093","localhost:9094","localhost:9097","localhost:9096"),
      false, executors, orderers)

    //9095 failed


    //====================================================================//
    //Start of ckite
    ord1.startCkite()
    ord2.startCkite()
    ord3.startCkite()
    ord4.startCkite()
    ord5.startCkite()
    ord6.startCkite()
    Thread.sleep(sleepTime)

    //===================================================================//
    //Execution phase to be measured
    val time1 = System.nanoTime


    //Setting up transactions
    val t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),35000, Application.A)   //give a enough balance
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")

    //Transfers

    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(100)

    for (i <- 1 to (TransactionNumber/Ordernumber) by 1){
      val t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage2 = RequestMessage(t2, 0, "cl1", orderers(1))
      val t3 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage3 = RequestMessage(t3, 0, "cl1", orderers(2))
      val t4 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage4 = RequestMessage(t4, 0, "cl1", orderers(3))
      val t5 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage5 = RequestMessage(t5, 0, "cl1", orderers(4))
      val t6 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage6 = RequestMessage(t6, 0, "cl1", orderers(5))
      val t7 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"),
        immutable.Seq[String]("a","b"),transferamount*10, Application.B)
      var transactionMessage7 = RequestMessage(t6, 0, "cl1", orderers.head)

      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(80)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage3)
      Thread.sleep(80)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage4)
      Thread.sleep(80)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage5)
      Thread.sleep(80)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage6)
      Thread.sleep(80)    //100 seems not enough here
      client.communication.sendMessage(transactionMessage7)
      Thread.sleep(80)    //100 seems not enough here
    }

    Thread.sleep(3*sleepTime)

    //====================================================================//
    //End of execution time
    val time2 = System.nanoTime

    //end of ckite
    ord1.stopCkite()
    ord2.stopCkite()
    ord3.stopCkite()
    ord4.stopCkite()
    ord5.stopCkite()
    ord6.stopCkite()
    Thread.sleep(sleepTime)
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

    timecounting(time1,time2,Executornumber,Ordernumber,TransactionNumber)
  }






}
