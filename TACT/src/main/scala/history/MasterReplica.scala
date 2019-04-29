package main.scala.history

import java.rmi.registry.LocateRegistry
import java.time.LocalDateTime

import main.scala.log.MasterImpl

/**
  * ECG History server class
  */
object MasterReplica {

  /**
    * Creates an registry for RMI and binds itself as EcgHistory
    *
    * @param args of type Array[String]
    */
  def main(args: Array[String]): Unit = {
    println("[" + LocalDateTime.now() + "][Master] Starting...")
    val registry = LocateRegistry.createRegistry(1099)
    println("[" + LocalDateTime.now() + "][Master] => Created registry")

    val server = new MasterImpl
    registry.rebind("EcgHistory", server)
    println("[" + LocalDateTime.now() + "][Master] => Bind ECG history")

    println("[" + LocalDateTime.now() + "][Master] RMI Registry started!")
    println("[" + LocalDateTime.now() + "][Master] Use Crtl+C to stop the server")
    println("--------------------------------------------------------------------------------------------")
    println()
  }
}