package com.in4086

import akka.actor.{Actor, Props}

object BackupSiteLogMessages {
  val RecordedCommitMessage: String = "RecordedCommit"
  val AbortMessage: String = "Abort"
  val EndOfTransactionMessage: String = "EndOfTransaction"
}

object BackupSiteMessages {

  case object RecordedCommit

  case object NoBackupSiteInfoAvailable

}

object BackupSiteServer {
  def props(name: String): Props = Props(new BackupSiteServer(name: String))
}

class BackupSiteServer(name: String) extends Actor {

  import BackupSiteLogMessages._
  import BackupSiteMessages._
  import ParticipantMessages._

  var logs: List[String] = Nil

  val timeToWriteLog: Int = Config.Latencies.timeToWriteLog
  val timeToWritePersistant: Int = Config.Latencies.timeToWritePersistent
  val latencyBackup: Int = Config.Latencies.timeToWriteLog

//  def print(funcName: String): Unit = {
//    println(this.sender().path.name + "->" + this.name + ": " + funcName)
//  }

  def receive: PartialFunction[Any, Unit] = {
    case DecidedToCommit =>
//      this.print("DecidedToCommit")
      Thread.sleep(timeToWriteLog)
      this.logs = RecordedCommitMessage :: this.logs
      Thread.sleep(timeToWritePersistant)
      Thread.sleep(latencyBackup)
      this.sender() ! RecordedCommit
    case GlobalAbort =>
//      this.print("GlobalAbort")
      Thread.sleep(timeToWriteLog)
      this.logs = AbortMessage :: this.logs
    case Inquire =>
//      this.print("Inquire")
      this.logs.head match {
        case AbortMessage =>
          Thread.sleep(latencyBackup)
          this.sender() ! GlobalAbort
        case RecordedCommitMessage =>
          Thread.sleep(latencyBackup)
          this.sender() ! GlobalCommit
        case EndOfTransactionMessage =>
          Thread.sleep(latencyBackup)
          this.sender() ! NoBackupSiteInfoAvailable
      }
    case EndOfTransaction =>
//      this.print("EndOfTransaction")
      Thread.sleep(timeToWriteLog)
      this.logs = EndOfTransactionMessage :: this.logs
    case _ =>
//      this.print(_)
  }
}
