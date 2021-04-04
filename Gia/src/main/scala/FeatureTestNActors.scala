package gia.core

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.{Config, ConfigFactory}
import gia.core.SimActor._

object FeatureTestNActors {

  private val r = scala.util.Random

  def main(args: Array[String]): Unit = {
    val superConfig = getConfig(9001)
    // Make SuperActor which keeps track of number of messages
    val superSystem = ActorSystem("gia", ConfigFactory.load(superConfig))
    val superIp = "localhost"
    val superActor = superSystem.actorOf(SuperActor.props(superIp))

    // Create n nodes, each having nFiles files, with the first node starting on port portNumber
    val nodes = 700
    val nFiles = 10
    val portNumber = 10001

    val replication = 7
    val iterations = 100

    for (i <- 0 until nodes) {
      println("---------------------------------------Setup actor " + i + "---------------------------------------")
      // Get all configuration in order
      val config = getConfig(portNumber + i)
      val system = ActorSystem("gia", ConfigFactory.load(config))
      var ip = "localhost"
      var port = portNumber + r.nextInt(i + 1) // Select the previous node as a neighbor
      val capacity = getCapacity
      if (i == 0) {
        // The first node is a special case
        ip = "first"
        port = 0
      }
      // Create the actor
      val actor = system.actorOf(SimActor.props(ip, port, capacity, superActor), "giaActor")
      // Add files to the actor
      for (j <- 0 until nFiles) {
        val filename = "file_" + i%(nodes/replication).toInt + "_" + j
        actor ! AddFile(new GiaFile(filename))
      }
      // Sleep isn't needed here, but creates a better overview
    }

    Thread.sleep(15*1000)
    for (i <- 0 until iterations) {
      val file = "file_" + r.nextInt(nodes/replication) + "_" + r.nextInt(nFiles)
      superActor ! SuperActor.Search(file, superActor)
      Thread.sleep(2*1000)
      superActor ! SuperActor.Report()
    }

    println("done")
    superActor ! SuperActor.ReportStatistics(iterations, replication)
  }

  private def getConfig(port: Int): Config = {
    ConfigFactory.parseString(
      """akka {
        loglevel="WARNING"
        actor {
          provider = "akka.remote.RemoteActorRefProvider"
          warn-about-java-serializer-usage = false
        }
        remote {
          enabled-transports = ["akka.remote.netty.tcp"]
          netty.tcp {
            hostname = "localhost"
            port = """" + port +
        """"
          }
        }
      }"""
    )
  }

  private def getCapacity: Int = {
    val randomCap = r.nextDouble()
    if (randomCap < 0.2) {
      r.nextInt(5) + 5
    } else if (randomCap < 0.65) {
      r.nextInt(16) + 10
    } else if (randomCap < 0.95) {
      r.nextInt(30) + 26
    } else if (randomCap < 0.999) {
      r.nextInt(55) + 56
    } else {
      r.nextInt(91) + 110
    }
  }

}
