package main.scala.history

import java.rmi.Naming
import java.rmi.registry.LocateRegistry
import java.time.LocalDateTime

import main.scala.tact.Tact

/**
  * ECG History server class
  */
object VoluntaryCoordinator {

  /**
    * Creates an registry for RMI and binds itself as EcgHistory
    *
    * @param args of type Array[String]
    */
  def main(args: Array[String]): Unit = {
    val rmiServer = args(0)
    val list = LocateRegistry.getRegistry(rmiServer).list()
    val r = new scala.util.Random()
    println("[" + LocalDateTime.now() + "][Coordinator] Coordinator started")
    println("[" + LocalDateTime.now() + "][Coordinator] => " + list.mkString(", "))
    println("--------------------------------------------------------------------------------------------")
    println()

    while (true) {
      val random = r.nextInt(list.length)
      val serverName = list(random)

      println("[" + LocalDateTime.now() + "][Coordinator] Check voluntary AntiEntropy session with " + serverName)

      if (serverName.contains("Replica")) {
        val server = Naming.lookup("//" + rmiServer + "/" + serverName) match {
          case s: Tact => s
          case other => throw new RuntimeException("Wrong objesct: " + other)
        }

        if (!server.isBusy) {
          println("[" + LocalDateTime.now() + "][Coordinator] => Start voluntary anti entropy session")
          server.startVoluntaryAntiEntropy()
        } else {
          println("[" + LocalDateTime.now() + "][Coordinator] => Server is busy")
        }
      }

      Thread.sleep(60000)
    }
  }
}