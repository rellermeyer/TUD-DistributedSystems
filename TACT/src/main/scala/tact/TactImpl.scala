package main.scala.tact

import java.rmi.server.UnicastRemoteObject
import java.time.LocalDateTime

import main.scala.database.Database
import main.scala.log.{Master, WriteLog, WriteLogItem, WriteOperation}
import main.scala.tact.conit.Conit
import main.scala.tact.manager.ConsistencyManager
import main.scala.tact.protocol.TwoRound

import scala.util.control.Breaks._

/**
  * TactImpl class.
  * Implements all the functions used with RMI for Tact Replicas
  *
  * @param replicaId  The identifier of the replica
  * @param ecgHistory The ECG history the replica uses to update errors
  */
class TactImpl(val replicaId: Char, val ecgHistory: Master, val rmiServer: String) extends UnicastRemoteObject with Tact {

  /**
    * The writeLog contains all writes that are made
    */
  var writeLog: WriteLog = new WriteLog()

  /**
    * Will contain all conits, one for each DB entry
    */
  var conits: Map[Char, Conit] = Map[Char, Conit]()

  /**
    * Each replica has a database, which will be updated by other replicas via the consistency manager
    */
  var database = new Database()

  /**
    * The consistency manager will keep track of all error variables in the replica
    */
  var manager = new ConsistencyManager(this)

  /**
    * AntiEntropy protocol that will be used.
    */
  var antiEntropy = new TwoRound(this)

  /**
    * If replica is busy with something.
    */
  var busy = false

  /**
    * Write item to replica. Writes to the conit, the writeLog and the ecgHistory
    *
    * @param key   The key which should be written
    * @param value The value which should be written
    */
  override def write(key: Char, value: Int): Unit = {
    busy = true
    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] Write key = " + key + ", value = " + value)
    val conit = getOrCreateConit(key)

    conit.update(value)
    writeLog.addItem(WriteLogItem(System.currentTimeMillis(), replicaId, WriteOperation(key, '+', value)))
    ecgHistory.write(WriteLogItem(System.currentTimeMillis(), replicaId, WriteOperation(key, '+', value)))

    if (manager.inNeedOfAntiEntropy(key)) {
      antiEntropy.start(key)
    }
    println()
    busy = false
  }

  /**
    * Reads the value of a specific conit
    *
    * @param key The key of the conit to be read
    * @return The value in the conit
    */
  override def read(key: Char): Int = {
    busy = true
    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] Read key = " + key)

    val conit = getOrCreateConit(key)
    busy = false
    conit.value
  }


  /**
    * Get or create a conit for a key
    *
    * @param key The key in the database.
    * @return
    */
  def getOrCreateConit(key: Char): Conit = {
    val optionalConit = conits.get(key)
    var conit: Conit = null

    if (optionalConit.isEmpty) {
      conit = createConit(key)
    } else {
      conit = optionalConit.get
    }

    conit
  }

  /**
    * Create a new conit for a key
    *
    * @param key The key in the database.
    * @return
    */
  private def createConit(key: Char): Conit = {
    var conit: Conit = null

    // Experiment 3.1
    if (key == 'a') {
      conit = new Conit(key, database.readValue(key), 0, 0, 0)
    }
    if (key == 'b') {
      conit = new Conit(key, database.readValue(key), Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
    }
    if (key == 'c') {
      conit = new Conit(key, database.readValue(key), 0, Integer.MAX_VALUE, Integer.MAX_VALUE)
    }
    if (key == 'd') {
      conit = new Conit(key, database.readValue(key), Integer.MAX_VALUE, 0, Integer.MAX_VALUE)
    }
    if (key == 'e') {
      conit = new Conit(key, database.readValue(key), Integer.MAX_VALUE, Integer.MAX_VALUE, 0)
    }

    // Experiment 3.2
    if (key == 'f') {
      conit = new Conit(key, database.readValue(key), 0, Integer.MAX_VALUE, Integer.MAX_VALUE)
    }
    if (key == 'g') {
      conit = new Conit(key, database.readValue(key), 3, Integer.MAX_VALUE, Integer.MAX_VALUE)
    }
    if (key == 'h') {
      conit = new Conit(key, database.readValue(key), 5, Integer.MAX_VALUE, Integer.MAX_VALUE)
    }

    // Experiment 3.3
    if (key == 'i') {
      conit = new Conit(key, database.readValue(key), Integer.MAX_VALUE, 0, Integer.MAX_VALUE)
    }
    if (key == 'j') {
      conit = new Conit(key, database.readValue(key), Integer.MAX_VALUE, 2, Integer.MAX_VALUE)
    }
    if (key == 'k') {
      conit = new Conit(key, database.readValue(key), Integer.MAX_VALUE, 10, Integer.MAX_VALUE)
    }


    // Experiment 1 & 2
    if (key == 'x') {
      conit = new Conit(key, database.readValue(key), 3, 3, 5000)
    }
    if (key == 'y') {
      conit = new Conit(key, database.readValue(key), 5, 5, 15000)
    }
    if (key == 'z') {
      conit = new Conit(key, database.readValue(key), 10, 10, 30000)
    }

    conits += (key -> conit)
    conit
  }

  /**
    * Writes a writeLog to the database
    *
    * @param writeLog of type WriteLog
    */
  def writeToDB(writeLog: WriteLog): Unit = {
    for (item: WriteLogItem <- writeLog.writeLogItems) {
      database.updateValue(item.operation.key, item.operation.value)
    }
  }

  /**
    * Accept the writeLog of another Replica.
    *
    * @param key      of type Char
    * @param writeLog of type WriteLog
    * @return Boolean
    */
  override def acceptWriteLog(key: Char, writeLog: WriteLog): Boolean = {
    busy = true
    println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] Accept WriteLog")
    for (item <- writeLog.writeLogItems) {
      breakable {
        val conit = getOrCreateConit(item.operation.key)

        // Skip writes that were written to this replica.
        if (item.replicaId.equals(replicaId)) {
          println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] -- Skip own write")
          break
        }

        if (manager.getTimeVector(item.replicaId, key) > item.timeVector) {
          println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] -- Skip time vector")
          break
        }

        println("[" + LocalDateTime.now() + "][Replica" + replicaId + "] -- Update with key = " + key + ", value = " + item.operation.value)
        manager.setTimeVector(item.replicaId, key, item.timeVector)

        conit.update(item.operation.value)
      }
    }
    println()
    busy = false

    true
  }

  override def currentTimeVector(replicaId: Char, key: Char): Long = manager.getTimeVector(replicaId, key)

  /**
    * Start an voluntary anti entropy sessions for each conit in the replica.
    */
  override def startVoluntaryAntiEntropy(): Unit = {
    for ((key, _) <- conits) {
      antiEntropy.start(key)
    }
  }

  override def isBusy: Boolean = busy
}
