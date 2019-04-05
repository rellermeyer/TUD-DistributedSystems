package com.in4086

import akka.actor.ReceiveTimeout

import scala.concurrent.duration._
import akka.actor.{Actor, ActorRef, Props}
import com.in4086.ClientMessages.TransactionFinished

object ParticipantLogMessages {
  val BeginTransactionMessage: String = "BeginTransaction"
  val ReadyMessage: String = "Ready"
  val DecidedToCommitMessage: String = "DecidedToCommit"
  val CommitMessage: String = "Commit"
  val AbortMessage: String = "Abort"
  val EndOfTransactionMessage: String = "EndOfTransaction"
}

object ParticipantMessages {

  final case class SetParticipants(refs: List[ActorRef])

  final case class SetBackupSites(refs: List[ActorRef])

  final case class DoCommit()

  final case class Prepare(backupSites: List[ActorRef])

  final case object VoteCommit

  final case object VoteAbort

  final case object DecidedToCommit

  final case object GlobalAbort

  final case object GlobalCommit

  final case object Acknowledgement

  final case object EndOfTransaction

  final case object Inquire

}

object BCStates {
  val Ready: String = "Ready"
  val Waiting: String = "Waiting"
}

object ParticipantServer {
  def props(name: String): Props = Props(new ParticipantServer(name: String))
}

class ParticipantServer(name: String) extends Actor {

  import BackupSiteMessages._
  import ParticipantMessages._
  import ParticipantLogMessages._

  // Persistent state variables
  var logs: List[String] = Nil

  // System participants references
  var participants: List[ActorRef] = Nil
  var ownBackupSites: List[ActorRef] = Nil
  var client: ActorRef = _

  // Transaction participants references
  var transactionCoordinator: ActorRef = _
  var coordinatorBackupSites: List[ActorRef] = Nil

  // Counters per transaction
  var voteCommitCount: Int = 0
  var acknowledgementCount: Int = 0
  var noBackupInfoAvailableCount: Int = 0

  // Is participant Coordinator?
  var isCoordinator: Boolean = false
  var commitSuccess: Boolean = true

  // Timeout thresholds
  var timeoutBlockingThreshold: Duration = Config.timeoutBlockingThreshold
  var timeoutBackupSiteThreshold: Duration = Config.timeoutBackupSiteThreshold

  val latencyLong: Int = Config.Latencies.latencyLong
  val latencyBackup: Int = Config.Latencies.latencyBackup
  val timeToWriteLog: Int = Config.Latencies.timeToWriteLog
  val timeToWritePersistant: Int = Config.Latencies.timeToWritePersistent

  // Vote abort probability
  var random = scala.util.Random

//  def print(funcName: String): Unit = {
//    println(this.sender().path.name + "->" + this.name + ": " + funcName)
//  }

  def receive: PartialFunction[Any, Unit] = {

    // Init
    case SetParticipants(refs) =>
//      this.print("SetParticipants")
      this.participants = refs
    case SetBackupSites(refs) =>
//      this.print("SetBackupSites")
//      this.print("Size of backupsites: " + refs.size)
      this.ownBackupSites = refs
    // Coordinator receives
    case TwoPCMessages.TransactionRequest =>
      // 1.1
      this.logs = BeginTransactionMessage :: this.logs
      Thread.sleep(timeToWriteLog)
      Thread.sleep(latencyLong)
      this.participants.foreach(p => p ! Prepare(this.ownBackupSites))
      this.isCoordinator = true
      this.client = this.sender()

    // Participant receives
    case Prepare(receivedCoordinatorBackupSites) =>
//      this.print("Prepare")
      // 1.2
      this.coordinatorBackupSites = receivedCoordinatorBackupSites
      this.transactionCoordinator = this.sender()
      Thread.sleep(timeToWriteLog)
      Thread.sleep(latencyLong)
      if (random.nextFloat() < Config.voteAbortProbability) {
        // Vote abort
        this.logs = AbortMessage :: this.logs
        this.sender() ! VoteAbort
      } else {
        // Vote commit
        this.logs = ReadyMessage :: this.logs
        this.sender() ! VoteCommit
        context.setReceiveTimeout(this.timeoutBlockingThreshold)
      }
    // Coordinator receives
    case VoteCommit =>
//      this.print("VoteCommit")
      this.voteCommitCount += 1
      if (this.voteCommitCount == this.participants.size) {
        // 2.1 - All participants have voted for commit
        Thread.sleep(timeToWriteLog)
        this.logs = DecidedToCommitMessage :: this.logs
//        this.print("Own backup sites size: " + this.ownBackupSites.size)

        Thread.sleep(latencyBackup)
        this.ownBackupSites.foreach(p => p ! DecidedToCommit)
        // Set timeout for no response from backup sites
        context.setReceiveTimeout(this.timeoutBackupSiteThreshold)
      }

    case VoteAbort =>
//      this.print("VoteAbort")
      // 2.1 - One of the participants has voted for abort
      if (this.logs.head != AbortMessage) {
        Thread.sleep(timeToWriteLog)
        this.logs = AbortMessage :: this.logs
        // Inform the backup sites (even though not really necessary)
        Thread.sleep(latencyBackup)
        this.commitSuccess = false
        this.ownBackupSites.foreach(b => b ! GlobalAbort)
        // Inform the participants to abort the transaction
        Thread.sleep(latencyLong)
        this.participants.foreach(b => b ! GlobalAbort)
      }

    // Coordinator receives from backup site
    case RecordedCommit =>
//      this.print("RecordedCommit")
      // 3.1 - Receive a single RecordedCommit from the backup sites
      if (this.logs.head == DecidedToCommitMessage) {
        Thread.sleep(timeToWriteLog)
        this.logs = CommitMessage :: this.logs
        // Send commit to participants
        // TODO: Possible timeout for acknowledgements here? Isn't mentioned in paper I believe
        Thread.sleep(timeToWritePersistant)
        Thread.sleep(latencyLong)
        this.commitSuccess = true
        this.participants.foreach(p => p ! GlobalCommit)
      }

    // Participant receives
    case GlobalCommit =>
//      this.print("GlobalCommit")
      // 3.2 - Participant commits
      Thread.sleep(timeToWriteLog)
      this.logs = CommitMessage :: this.logs
      Thread.sleep(timeToWritePersistant)
      Thread.sleep(latencyLong)
      this.transactionCoordinator ! Acknowledgement
    case GlobalAbort =>
//      this.print("GlobalAbort")
      // 3.2 - Participant aborts
      Thread.sleep(timeToWriteLog)
      this.logs = AbortMessage :: this.logs
      Thread.sleep(latencyLong)
      this.transactionCoordinator ! Acknowledgement

    // Coordinator receives
    case Acknowledgement =>
      this.acknowledgementCount += 1
      // check received acknowledgement of all participants
      if (this.acknowledgementCount == this.participants.size) {
        // 3.3 - Coordinator receives acknowledgement from all participants

        // TODO: It is not mentioned in the paper that the coordinator sends end of transaction to all backup sites
        Thread.sleep(latencyBackup)
        this.ownBackupSites.foreach(b => b ! EndOfTransaction)
        Thread.sleep(timeToWriteLog)
        this.logs = EndOfTransactionMessage :: this.logs
        // Finalize transaction and reset counters
        this.client ! TransactionFinished(this.commitSuccess)
        this.isCoordinator = false
        this.acknowledgementCount = 0
        this.voteCommitCount = 0
        this.noBackupInfoAvailableCount = 0
//        this.client = null
      }

    // Both receive
    case ReceiveTimeout =>
//      this.print("ReceiveTimeout")
      if (this.isCoordinator) {
        this.logs.head match {
          case DecidedToCommitMessage =>
            // 3.1 No recorded commit messages from backup sites
            // No RecordedCommit received from any backup site, and timeout is reached
            Thread.sleep(timeToWriteLog)
            this.logs = AbortMessage :: this.logs
            Thread.sleep(latencyLong)
            this.commitSuccess = false
            this.participants.foreach(p => p ! GlobalAbort) // abort
          case BeginTransactionMessage =>
            // No vote response from all participants
            Thread.sleep(timeToWriteLog)
            this.logs = AbortMessage :: this.logs
            Thread.sleep(latencyLong)
            this.commitSuccess = false
            this.participants.foreach(p => p ! GlobalAbort) // abort
          case _ =>
          // Timeout is triggered but Coordinator was already in a different state, so do nothing
        }
      } else {
        // 3.2 Termination Protocol handling:
        // No response after sending vote commit
        // Ignore if already decided to commit/abort
        if (this.logs.nonEmpty && this.logs.head == ReadyMessage) {
          // Participant is still waiting for a response
          Thread.sleep(latencyLong)
          this.participants.foreach(p => p ! Inquire)
          Thread.sleep(latencyBackup)
          this.coordinatorBackupSites.foreach(p => p ! Inquire)
          // Set a new timeout, since no valid response has been received yet.
          context.setReceiveTimeout(this.timeoutBlockingThreshold)
        }
      }

    case Inquire =>
//      this.print("Inquire")
      // 3.2-1 Participant is asked for commit/abort vote
      if (this.logs.nonEmpty) {
        this.logs.head match {
          case CommitMessage =>
            // Participant has committed
            Thread.sleep(latencyLong)
            this.sender() ! GlobalCommit
            this.commitSuccess = true
          case AbortMessage =>
            // Participant has aborted
            Thread.sleep(latencyLong)
            this.sender() ! GlobalAbort
            this.commitSuccess = false
          case _ => // Do nothing
        }
      }

    case NoBackupSiteInfoAvailable =>
//      this.print("NoBackupSiteInfoAvailable")
      this.noBackupInfoAvailableCount += 1
      if (this.noBackupInfoAvailableCount == this.coordinatorBackupSites.size) {
        // 3.2-3 No backup info available at all sites
        Thread.sleep(timeToWriteLog)
        this.logs = AbortMessage :: this.logs
        this.noBackupInfoAvailableCount = 0
        // TODO: finalize transaction here?
      }
  }
}
