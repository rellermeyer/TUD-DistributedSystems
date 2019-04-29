package main.scala.tact.protocol

import java.rmi.Naming
import java.rmi.registry.LocateRegistry
import java.time.LocalDateTime

import main.scala.tact.{Tact, TactImpl}

/**
  * One-Round protocol.
  */
class OneRound(replica: TactImpl) extends Serializable with RoundProtocol {

  /**
    * Start the round protocol.
    */
  override def start(key: Char): Unit = {
    println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] Anti-entropy session for key = " + key)
    val writeLog = replica.writeLog.getWriteLogForKey(key)

    for (server <- LocateRegistry.getRegistry(replica.rmiServer).list()) {
      if (server.contains("Replica") && !server.endsWith(replica.replicaId.toString)) {
        println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] => Start anti-entropy session with " + server)

        val rep = Naming.lookup("//" + replica.rmiServer + "/" + server) match {
          case s: Tact => s
          case other =>
            println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] Error cannot find " + server)
            throw new RuntimeException("Error cannot find " + server)
        }

        rep.acceptWriteLog(key, writeLog)
        for (item <- writeLog.writeLogItems) {
          println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] \t Push key = "+ item.operation.key + " value = " + item.operation.value)
        }

        println("[" + LocalDateTime.now() + "][Replica" + replica.replicaId + "] => Finished anti-entropy session with " + server)
      }
    }

    replica.writeToDB(writeLog)
    replica.writeLog.flush(key)
  }
}
