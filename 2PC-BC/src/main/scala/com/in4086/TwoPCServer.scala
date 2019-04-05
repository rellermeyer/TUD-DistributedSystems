package com.in4086

import akka.actor.{Actor, ActorRef, Props, ReceiveTimeout}
import com.in4086.ClientMessages.TransactionFinished

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.util.Random

object States {
  val Ready: String = "Ready"
  val Blocked: String = "Blocked"
}

object TwoPCMessages {

  case object TransactionRequest

  case class QueryToCommit(message: String)

  case object VoteYes

  case object VoteNo

  case object Commit

  case object Rollback

  case object Acknowledgment

  case class AddParticipants(n: Int, refs: ArrayBuffer[ActorRef])

}

object TwoPCServer {
  def props(name: String): Props = Props(new TwoPCServer(name: String, probOfFailure = Config.voteAbortProbability))
}

class TwoPCServer(name: String, probOfFailure: Double) extends Actor {

  import TwoPCMessages._

  val random = new Random()
  val latency: Int = Config.Latencies.latencyLong
  val timeToWrite: Int = Config.Latencies.timeToWritePersistent

  var timeoutBlockingThreshold: Duration = Config.timeoutBlockingThreshold

  var participants: ArrayBuffer[ActorRef] = new ArrayBuffer[ActorRef]()
  var client: ActorRef = null

  var commitSuccess: Boolean = true

  var nrParticipants: Int = 0

  var nrYesses: Int = 0
  var nrAcks: Int = 0

  var state = States.Ready

  def print(funcName: String): Unit = {
//    println(this.sender().path.name + "->" + this.name + ": " + funcName)
  }

  def receive = {
    // FOR INIT
    case AddParticipants(n, refs) =>
      participants ++= refs
      nrParticipants = n

    // FOR COORDINATOR
    case TransactionRequest =>
      this.print("TxRequest")
      if (this.state != States.Blocked) {
        this.client = this.sender()
        this.commitSuccess = true
        nrYesses = 0
        nrAcks = 0
        this.state = States.Blocked
        Thread.sleep(latency)
        context.setReceiveTimeout(this.timeoutBlockingThreshold)
        this.participants.foreach(
          (participant: ActorRef) => participant ! QueryToCommit("")
        )
      }

    // FOR PARTICIPANT
    case QueryToCommit(message: String) =>
      this.print("QueryToCommit")
      this.state = States.Blocked
      Thread.sleep(latency)
      if (random.nextDouble() <= probOfFailure) {
        this.sender() ! VoteNo
      } else {
        this.sender() ! VoteYes
      }

    // FOR COORDINATOR
    case VoteYes =>
      this.print("VoteYes")
      Thread.sleep(latency)
      nrYesses += 1
      if (nrYesses == nrParticipants) {
        this.participants.foreach(
          (participant: ActorRef) => participant ! Commit
        )
      }

    // FOR COORDINATOR
    case VoteNo =>
      this.print("VoteNo")
      if (this.commitSuccess) {
        Thread.sleep(latency)
        this.commitSuccess = false
        this.participants.foreach(
          (participant: ActorRef) => participant ! Rollback
        )
      }

    // FOR PARTICIPANT
    case Commit =>
      this.print("Commit")
      Thread.sleep(latency + timeToWrite)
      this.sender() ! Acknowledgment
      this.state = States.Ready

    // FOR PARTICIPANT
    case Rollback =>

      this.print("Rollback")
      Thread.sleep(latency + timeToWrite)
      this.sender() ! Acknowledgment
      this.state = States.Ready

    // FOR COORDINATOR
    case Acknowledgment =>
      this.print("Ack")
      nrAcks += 1
      if (nrAcks == nrParticipants) {
        this.state = States.Ready
        this.client ! TransactionFinished(commitSuccess)
      }

    case ReceiveTimeout =>
      this.print("Timeout")
      // No VoteYes from all participants
      Thread.sleep(latency)
      this.commitSuccess = false
      this.participants.foreach(
        (participant: ActorRef) => participant ! Rollback
      )
  }
}
