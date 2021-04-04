package main.scala.tact.protocol

import java.rmi.Naming
import java.rmi.registry.LocateRegistry

import main.scala.tact.{Tact, TactImpl}

/**
  * Two-Round protocol.
  */
class TwoRound(replica: TactImpl) extends RoundProtocol {

  /**
    * Start the round protocol.
    */
  override def start(key: Char): Unit = {
    for (server <- LocateRegistry.getRegistry(replica.rmiServer).list()) {
      if (server.contains("Replica") && !server.endsWith(replica.replicaId.toString)) {
        println("Start anti-entropy session with " + server)

        val rep = Naming.lookup("//" + replica.rmiServer + "/" + server) match {
          case s: Tact => s
          case other => throw new RuntimeException("Error: " + other)
        }

        var writeLog = replica.writeLog.partition(rep.currentTimeVector(replica.replicaId, key))
        writeLog = writeLog.getWriteLogForKey(key)
        rep.acceptWriteLog(key, writeLog)

        println("Finished anti-entropy session with " + server)
      }
    }

    replica.writeToDB(replica.writeLog)
    replica.writeLog.flush(key)
  }
}
