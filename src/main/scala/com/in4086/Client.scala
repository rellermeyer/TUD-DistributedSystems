package com.in4086

import akka.actor.{ActorRef, ActorSystem}
import com.in4086.ClientMessages.StartSending
import com.in4086.ParticipantMessages.{SetBackupSites, SetParticipants}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

object Config {
  var CommitType = "BC"
  var NrRounds = 3

  var NrParticipants = 6 // 3, 6, 18
  var NrBackupSites = 6 // 3, 6, 18
  var NrTransactions = 100 // 50, 100, 250, 500

  var voteAbortProbability = 0.05 // 0, .01, .05, .1

  var timeoutBlockingThreshold: Duration = 10000 milliseconds // 1000, 10000
  var timeoutBackupSiteThreshold: Duration = 10000 milliseconds // 1000, 10000

  object Latencies {
    var latencyLong: Int = 100 // 50 , 250 , 500, 750
    var latencyBackup: Int = 1 // 1
    var timeToWriteLog: Int = 20 // 0, 10, 20
    var timeToWritePersistent: Int = 20 // 10, 20, 50
  }
}

object Init {
  def initParticipants(system: ActorSystem, n: Int): ArrayBuffer[ActorRef] = {
    val res = new ArrayBuffer[ActorRef](n)
    for (i <- 1 to n) {
      val name = "Server" + i
      res += system.actorOf(TwoPCServer.props(name), name)
    }
    return res
  }

  def initBCParticipants(system: ActorSystem, n: Int): List[ActorRef] = {
    var res: List[ActorRef] = Nil
    for (i <- 1 to n) {
      val name = "Server" + i
      res = system.actorOf(ParticipantServer.props(name), name) :: res
    }
    return res
  }

  def initBCBackupSites(system: ActorSystem, n: Int, serverName: String): List[ActorRef] = {
    var res: List[ActorRef] = Nil
    for (i <- 1 to n) {
      val name = "Backup" + serverName + "-" + i
      res = system.actorOf(BackupSiteServer.props(name), name) :: res
    }

    return res
  }
}

object Helpers {

}

object Client extends App {

  import Config._
  import TwoPCMessages._
  import Init._

  def updateConfig(changeParam: String, changeValue: String): Unit ={
    changeParam match {
      case "NrParticipants" => Config.NrParticipants = changeValue.toInt
      case "NrBackupSites" => Config.NrBackupSites = changeValue.toInt
      case "NrTransactions" => Config.NrTransactions = changeValue.toInt
      case "voteAbortProbability" => Config.voteAbortProbability = changeValue.toFloat
      case "timeoutBackupSiteThreshold" => Config.timeoutBackupSiteThreshold = changeValue.toInt milliseconds
      case "timeoutBlockingThreshold" => Config.timeoutBlockingThreshold = changeValue.toInt milliseconds
      case "latencyLong" => Config.Latencies.latencyLong = changeValue.toInt
      case "timeToWriteLog" => Config.Latencies.timeToWriteLog = changeValue.toInt
      case "timeToWritePersistent" => Config.Latencies.timeToWritePersistent = changeValue.toInt
      case _ => {
        System.out.println("Unknown parameter: " + _)
        System.exit(1)
      }
    }
  }

  override def main(args: Array[String]){
    val system: ActorSystem = ActorSystem("MultiNodeCluster")

    Config.CommitType = args(0)
    val changeParam = args(1)
    val changeValue = args(2)

    updateConfig(changeParam, changeValue)

    if (Config.CommitType == "2PC") {
      val participants: ArrayBuffer[ActorRef] = initParticipants(system, NrParticipants)
      participants.foreach(participant => {
        participant ! AddParticipants(NrParticipants - 1, participants.filter(p2 => !p2.equals(participant)))
      })

      val client = system.actorOf(ClientServer.props(participants, NrTransactions, system), "client")
      client ! StartSending
    } else {
      val participants: List[ActorRef] = initBCParticipants(system, NrParticipants)

      participants.foreach(participant => {
        val backupSites = initBCBackupSites(system, NrBackupSites, participant.path.name)

        participant ! SetParticipants(participants.filter(p2 => !p2.equals(participant)))
        participant ! SetBackupSites(backupSites)
      })

      val client = system.actorOf(ClientServer.props(participants.to[ArrayBuffer], NrTransactions, system), "client")
      client ! StartSending
    }

  }
}
