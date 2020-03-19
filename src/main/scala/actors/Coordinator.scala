package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import util._
import Messages._

import scala.collection.mutable.ListBuffer

object Coordinator {
  var coordinators: Set[Coordinator] = null
  var participants: Set[Participant] = Set()
  var BaPrepared: Boolean = false
  var BaCommitted: Boolean = false
  var BaPrepareLog: ListBuffer[Messages.BaPrepare] = ListBuffer()
  var BaCommitLog: ListBuffer[Messages.BaCommit] = ListBuffer()

  def apply(): Behavior[CoordinatorMessage] = Behaviors.receive { (context, message) =>

    //TODO: implement message content, message checking and byzantine behaviour simulation

    message match {
      case m: SendCoordinatorSet =>
        coordinators = m.coordinatorSet

      case RegisterWithCoordinator(from: Participant) =>
        participants += from

      case m: Prepared =>
        context.log.info("Prepared message received from participant: " + m.from + ". Transaction: " + m.t)

      case m: InitCommit => //After receiving initCommit message, coordinator answers with a prepare message
        context.log.info("InitCommit received from " + m.from + ". Transaction: " + m.t)
        participants.foreach(participant => participant ! Messages.Prepare(context.self))
        //Start byzantine agreement
        byzantineAgreement(coordinators, m.proposeCommit, context.self) //Set of coordinator replicas answers

      case m: CommitOutcome =>
        if(m.commitResult) {
          context.log.info("Committed received from participant: " + m.from + ".Transaction: " + m.t)
        } else {
          context.log.info("Aborted received from participant: " + m.from + ".Transaction: " + m.t)
        }

      case InitViewChange(from: Coordinator) =>
      case m: NewView =>
      case m: BaPrepare =>
        //TODO: check message validity and only append valid messages (or append all and do validation in checkBaPrepare?)
        context.log.info("Received BaPrepare")
        BaPrepareLog += m
        if (enoughMatchingBaPrepare(BaPrepareLog,m.proposeCommit)&& (!BaPrepared)) {
          //BaPrepared flag prevents duplicate messages
          //TODO: implement this in a way that functions for continuous operation instead of just one commit
          coordinators.foreach(coord => coord ! Messages.BaCommit(null, null, m.proposeCommit, context.self))
          BaPrepared = true
          context.log.info("BaPrepared")
        }
        case m: BaCommit =>
        //TODO: check message validity and only append valid messages (or append all and do validation in checkBaCommit?)
        context.log.info("Received BaCommit")
        BaCommitLog += m
        if (enoughMatchingBaCommit(BaCommitLog,m.proposeCommit)&& (!BaCommitted)) {
          //BaCommitted flag prevents duplicate messages
          //TODO: implement this in a way that functions for continuous operation instead of just one message
          participants.foreach(part => part ! Messages.Commit(context.self, m.proposeCommit))
          BaCommitted = true
          context.log.info("BaCommitted")
        }
      case m: BaPrePrepare =>
        //TODO: implement message checking and handling the reject case (initiating view change)
        coordinators.foreach(coord => coord ! Messages.BaPrepare(null, null, m.proposeCommit,context.self))
        context.log.info("BaPrePrepared")

      case SendUnknownParticipants(participants: Set[Participant], from: Coordinator) =>
        this.participants |= participants

      case RequestUnknownParticipants(from: Coordinator) =>
        from ! Messages.SendUnknownParticipants(participants, context.self)

    }
    Behaviors.same
  }

  def byzantineAgreement(coordinators: Set[Coordinator], proposeCommit: Boolean/*true =commit,false=abort*/, self: ActorRef[CoordinatorMessage]): Unit = {
    // Start BAAlgorithm
    coordinators.foreach(coord => coord ! Messages.BaPrePrepare(null, null, proposeCommit, self))
  }


  def enoughMatchingBaPrepare(/*v: View, t: Transaction, */ BaPrepareLog: ListBuffer[Messages.BaPrepare],proposedOutcome: Boolean): Boolean = {
    var numOfMatchingMessages = 0

    BaPrepareLog.foreach(x =>
      //TODO: select only matching messages
      if (x.proposeCommit == proposedOutcome) {
        numOfMatchingMessages = numOfMatchingMessages + 1
      }
    )
    val f = getF()
    if (numOfMatchingMessages >= 2 * f) true else false
  }

  def enoughMatchingBaCommit(BaCommitLog: ListBuffer[BaCommit],proposedOutcome: Boolean): Boolean = {
    var numOfMatchingMessages = 0

    BaCommitLog.foreach(x =>
      //TODO: select only matching messages
      if (x.proposeCommit == proposedOutcome) {
        numOfMatchingMessages = numOfMatchingMessages + 1
      }
    )
    val f = getF()
    if (numOfMatchingMessages > 2 * f) true else false
  }

  def getF(): Integer = {
    val f: Integer = (coordinators.size - 1) / 3
    return f
  }
}