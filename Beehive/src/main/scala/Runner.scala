package Beehive

import java.io.{BufferedWriter, File, FileWriter, PrintWriter}

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Duration, TimeoutException}

import scala.collection.mutable
import scala.util.Random

object Runner {

  def main(args: Array[String]) {


    var n = 10
    if (args.length < 1) {

      println("Not enough arguments, starting with default 10 nodes.")
    } else {
      n = Integer.valueOf(args(0))
    }


    var ids: List[Int] = List()
    val startID = 8000
    for (i <- 0 until n) {
      // Start nodes with ids of 8000 + 20n
      ids = (startID + 20 * i) :: ids
    }

    // Use random order for node id's
    ids = Random.shuffle(ids)
    println(ids)

    val beehiveClientMap = new mutable.HashMap[Int, BeehiveClient]()
    val clientMap = new mutable.HashMap[Int, Service[Request, Response]]()

    for (i <- ids) {
      val client: Service[Request, Response] = Http.newService(s"localhost:${i}")
      clientMap(i) = client
    }

    // Start nodes
    for (i <- ids) {
      val thread = new Thread {
        override def run {
          val id = i
          val portEnding = i
          val numPorts = n
          println(s"starting server with id = $id on port = $portEnding")

          // setup server to process requests
          val beehiveClient = new BeehiveClient(clientMap)
          beehiveClientMap(i) = beehiveClient
          val beehiveService = new BeehiveService(id, portEnding, numPorts, beehiveClient, ids)
          val server = Http.serve(s":${portEnding}", beehiveService)

          Await.ready(server)
        }
      }
      thread.start()
    }

    // Keep track of items in the system
    var itemList: List[Int] = List()
    for (i <- ids) {
      itemList = itemList ++ List(i, i + 5, i - 5)
    }

    Thread.sleep(4000)

    val shuffledItemList: List[Int] = Random.shuffle(itemList)
    val beehiveClient = new BeehiveClient(clientMap)
    var averageHops = 0
    var notfound = 0
    // Test how the amount of hops changes over time


    //create evaluation file
    val file = new File("eval_no_remove.txt")
    val bw = new BufferedWriter(new FileWriter(file, true))
    val pw = new PrintWriter(bw);


    for (i <- 0 to Integer.MAX_VALUE) {

      val itemIndex: Int = Random.nextInt(math.max(1, i % shuffledItemList.length))
      val serviceIndex: Int = Random.nextInt(ids.length)

      try {
        Await.result(beehiveClient.lookup(shuffledItemList(itemIndex), ids(serviceIndex), 0, 1)
          .onSuccess { rep: Response =>
          if (rep.getContentString().equals("-1") || rep.getContentString() == "") {
//            println("VALUE WAS NOT FOUND")
            notfound = notfound + 1
          } else {
            val response = rep.getContentString().split(" ")
            val value = response(0)
            val hops = response(1)

            averageHops = averageHops + hops.toInt
//            println(s"${hops} for item with value ${value}")
          }
        }, Duration.fromSeconds(1))
      } catch {
          case _: TimeoutException => {
//            System.err.println(s"${ids(serviceIndex)} Timed Out!")
          }
          case e: Throwable => System.err.println(s"${e.toString} exception!")
      }

      if (i % 100 == 0) {
        println(s"AT ROUND ${i}")
        println(s"TOTAL HOPS WAS: ${averageHops/(100.0 - notfound)}")
        println(s"TOTAL not found: ${notfound}")
        pw.println(s"${i} ${averageHops/(100.0 - notfound)} ${notfound}")
        pw.flush()
        averageHops = 0
        notfound = 0
      }
    }


    bw.close()
  }
}
