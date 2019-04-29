package main.scala.tact

import java.rmi.{Remote, RemoteException}

import main.scala.log.WriteLog

/**
  * Trait for the Tact Replicas
  * For documentation, see TactImplImpl
  */
trait Tact extends Remote {

  @throws(classOf[RemoteException])
  def write(key: Char, value: Int): Unit

  @throws(classOf[RemoteException])
  def read(key: Char): Int

  @throws(classOf[RemoteException])
  def acceptWriteLog(key: Char, writeLog: WriteLog): Boolean

  @throws(classOf[RemoteException])
  def currentTimeVector(replicaId: Char, key: Char): Long

  @throws(classOf[RemoteException])
  def startVoluntaryAntiEntropy(): Unit

  @throws(classOf[RemoteException])
  def isBusy: Boolean
}
