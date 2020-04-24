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

File create to specific test the performance when multiple transactions are created and propagation inside the system

 */


class TransactionNumtest extends FlatSpec with Matchers {

  def timecounting(start : Long, end : Long, transactions : Int): Unit = {          //function for time calculation & results output
    val duration = (end - start) / 1e9d    //end time counting
    val pw = new FileWriter("Number of transactions test.txt" ,true)
    pw.write("With " + transactions + " transactions, test took : " + duration + " Seconds" + "\n")
    pw.close()
  }


  "Test for test multiple transactions inside the system , 1 order, 2 executor" should "test pass" in {

    //============================================================//
    //Initial phase, setting up the node, orders,excutors and clients
    val TransactionNumber = 10        // The number which the loop counts ie. the transactions inside the system

    val executors = immutable.Seq[String]("ex1","ex2")
    val orderers = immutable.Seq[String]("ord1")
    val client = new Node("cl1", executors, orderers)
    val agents = mutable.Map[String, mutable.Set[Application]]()
    agents.put("ex1", mutable.Set[Application](Application.A))
    agents.put("ex2", mutable.Set[Application](Application.B))
    val ex1 = new Executor(mutable.Set(Application.A), agents, "ex1", executors, orderers)
    val ex2 = new Executor(mutable.Set(Application.B), agents, "ex2", executors, orderers)
    val order = new Order("ord1", "localhost:9091", immutable.Seq[String](), true, executors, orderers)
    val sleepTime = 1000
    val transferamount = 10         //This amount got some problem here


    //====================================================================//
    //Start of ckite
    order.startCkite()
    Thread.sleep(sleepTime)

    //===================================================================//
    //Execution phase to be measured
    val time1 = System.nanoTime


    //Setting up transactions

    val t1 = new Transaction(0, Operation.set, "a", None, immutable.Seq[String](), immutable.Seq[String]("a"),15000, Application.A)   //give a enough balance
    var transactionMessage1 = RequestMessage(t1, 0, "cl1", "ord1")

    client.communication.sendMessage(transactionMessage1)
    Thread.sleep(1000)

    for (i <- 2 to TransactionNumber by 1){
      val t2 = new Transaction(i, Operation.transfer, "a", Some("b"), immutable.Seq[String]("a"), immutable.Seq[String]("a","b"),transferamount, Application.B)
      var transactionMessage2 = RequestMessage(t2, 0, "cl1", "ord1")
      client.communication.sendMessage(transactionMessage2)
      Thread.sleep(200)    //100 seems not enough here
    }

    Thread.sleep(1*sleepTime)

    //====================================================================//
    //End of execution time
    val time2 = System.nanoTime

    //end of ckite
    order.stopCkite()
    //====================================================================//


//    order.currentBlock.getTransactions.length shouldBe 0        //This one becomes 2?
//    val aRes = 14700
//    val bRes = 300
//    for (ex <- Array(ex1, ex2)) {
//      ex.state.getBalance("a") shouldBe Some(aRes)
//      ex.state.getBalance("b") shouldBe Some(bRes)
//    }

    timecounting(time1,time2,TransactionNumber)
  }

}
