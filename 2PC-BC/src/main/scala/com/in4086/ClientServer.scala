package com.in4086

import java.io.{BufferedWriter, File, FileWriter}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.in4086.ClientMessages.{StartSending, TransactionFinished}
import com.in4086.ParticipantMessages.DoCommit
import com.in4086.TwoPCMessages.TransactionRequest

import scala.collection.mutable.ArrayBuffer

object ClientMessages {

  case object StartSending

  case class TransactionFinished(succeeded: Boolean)

}

object ClientServer {
  def props(participants: ArrayBuffer[ActorRef], nrMessages: Int, system: ActorSystem): Props =
    Props(new ClientServer(participants, nrMessages, system))
}

class ClientServer(participants: ArrayBuffer[ActorRef], nrMessages: Int, system: ActorSystem) extends Actor {
  var nrRounds: Int = Config.NrRounds
  var startTime: Long = 0

  val rand = scala.util.Random
  var nrMessagesLeft = nrMessages
  val nrParticipants = participants.size
  var throughput = 0

  def printStatistics(round: Int, startTime: Long, throughput: Int): Unit = {
    val outputPrefix = "[OUTPUT] "
    val runTime: Long = System.currentTimeMillis() - startTime
    val divider = "=================================================================="

    val header = "Round,CommitType,runTime,throughput,msg/s,NrTransactions,NrBackupSites,NrParticipants,timeoutBackupSiteThreshold" +
      ",timeoutBlockingThreshold,voteAbortProbability,latencyLong,latencyBackup,timeToWriteLog,timeToWritePersistent"
    val values = Array(
      round,
      Config.CommitType,
      runTime.toString + " milliseconds",
      throughput,
      throughput / (runTime / 1000),
      Config.NrTransactions,
      Config.NrBackupSites,
      Config.NrParticipants,
      Config.timeoutBackupSiteThreshold,
      Config.timeoutBlockingThreshold,
      Config.voteAbortProbability,
      Config.Latencies.latencyLong,
      Config.Latencies.latencyBackup,
      Config.Latencies.timeToWriteLog,
      Config.Latencies.timeToWritePersistent
    )

    val file = new File("./results.csv")
    val writer = new BufferedWriter(new FileWriter(file, true))
    writer.write(values.mkString(","))
    writer.newLine()
    writer.close()

    println(divider)
    println(outputPrefix + header)
    println(outputPrefix + values.mkString(","))
    println(divider)
  }

  def receive = {
    case StartSending =>
      val nextParticipant = rand.nextInt(nrParticipants)
      this.startTime = System.currentTimeMillis()
      this.throughput = 0
      participants(nextParticipant) ! TransactionRequest
    case TransactionFinished(succeeded: Boolean) =>
      println("messages to go: " + nrMessagesLeft)
      nrMessagesLeft -= 1
      if (succeeded) {
        throughput += 1
        println("succeeded")
      } else {
        println("aborted")
      }
      if (nrMessagesLeft > 0) {
        val nextParticipant = rand.nextInt(nrParticipants)
        participants(nextParticipant) ! TransactionRequest
      } else {
        printStatistics(Config.NrRounds - nrRounds, startTime, throughput)
        nrMessagesLeft = nrMessages
        nrRounds -= 1
        if (nrRounds > 0) {
          self ! StartSending
        } else {
          system.terminate()
        }
      }
  }
}
