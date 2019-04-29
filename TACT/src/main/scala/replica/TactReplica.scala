package main.scala.replica

import java.rmi.Naming
import java.time.LocalDateTime

import main.scala.log.Master
import main.scala.tact.TactImpl

/**
  * Class TactReplicaA
  */
object TactReplica {

  /**
    * Starts a Tact Replica (ID = A)
    * Looks up the ECG and gives it to the created tact
    * Does some basic write and read operations
    *
    * @param args of type Array[String]
    */
  def main(args: Array[String]): Unit = {
    val rmiServer = args(0)
    val replicaId = args(1).toCharArray()(0)

    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] Starting Replica" + replicaId)

    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] => Looking for ECG history")
    val server = Naming.lookup("//" + rmiServer + "/EcgHistory") match {
      case s: Master => s
      case other => throw new RuntimeException("Wrong objesct: " + other)
    }
    server.debug("Registered Replica" + replicaId)

    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] => Binding TACT Replica to RMI")
    val replica = new TactImpl(replicaId, server, rmiServer)
    server.register("//" + rmiServer + "/Replica" + replicaId, replica)

    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] Replica started on " + "rmi://" + rmiServer + "/Replica" + replicaId)
    println("--------------------------------------------------------------------------------------------")
    println()
  }
}
