package main.scala.log

import java.rmi.server.UnicastRemoteObject
import java.rmi.{Naming, Remote}
import java.time.LocalDateTime

import scala.collection.immutable.TreeMap

/**
  * EcgLogImpl class.
  * Implements all the functions used with RMI for Ecg History
  */
class MasterImpl extends UnicastRemoteObject with Master {

  var trueValMap: Map[Char, Int] = TreeMap[Char, Int]()

  /**
    * The writeLog contains all writes that are made
    */
  var writeLog: WriteLog = new WriteLog()

  /**
    * Writes an WriteLogItem to the writeLog
    *
    * @param item of type WriteLogItem
    */
  override def write(item: WriteLogItem): Unit = {
    println("[" + LocalDateTime.now() + "][Master] Write ECG writelog: " + item)

    val value = item.operation.value
    val key = item.operation.key

    if (!trueValMap.contains(key)) trueValMap += (key -> value) else trueValMap += (key -> (trueValMap(key) + value))

    writeLog.addItem(item)
  }

  /**
    * Returns the WriteLog
    *
    * @return of type WriteLog
    */
  override def read(): WriteLog = {
    println("[" + LocalDateTime.now() + "][Master] Read ECG writelog")

    writeLog
  }

  override def debug(message: String): Unit = {
    println("[" + LocalDateTime.now() + "][Master] " + message)
  }

  override def register(name: String, obj: Remote): Unit = {
    Naming.bind(name, obj)
    println("[" + LocalDateTime.now() + "][Master] Registered " + name)
  }

  override def originalValues(): Map[Char, Int] = {
    trueValMap.toSeq.sortBy(_._1)

    trueValMap
  }
}
