package chubby.client

import java.io.{BufferedReader, InputStreamReader}
import java.net.URL

import akka.actor.ActorSystem
import chubby.grpc.LeaderRequest
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Random

object MainClient {
  def run(): Unit = {
    var allReplica = List.empty[String]

    val bufferedSource = Source.fromFile("serverips.txt")
    for (line <- bufferedSource.getLines) {
      allReplica = line :: allReplica
    }

    val client = new Client(allReplica)

    val allLock = Seq(
      "A",
      "B",
      "C",
      "D",
      "E",
      "F",
      "G",
      "H",
      "I",
      "J",
      "K",
      "L",
      "M",
      "N",
      "O",
      "P",
      "Q",
      "R",
      "S",
      "T",
      "U",
      "V",
      "W",
      "X",
      "Y",
      "Z",
      "1",
      "2",
      "3",
      "4",
      "5",
      "6",
      "7",
      "8",
      "9",
      "!",
      "@",
      "#",
      "$",
      "%"
    )
    val random = new Random()
    var count = 0;

    while (true) {
      val currentLock = allLock(random.nextInt(allLock.length))
      count = count + 1

      println("start " + currentLock)
      if (count % 5 == 0) {
        client.lock(currentLock)(println("Locked " + currentLock))
      } else {
        client.read(currentLock)(println("Read " + currentLock))
      }

      Thread.sleep(1000)
    }
  }
}
