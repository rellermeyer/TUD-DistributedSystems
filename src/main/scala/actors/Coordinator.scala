package actors

import actors.Coordinator.BaState.BaState
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import util.Messages.CommitOrAbort.CommitOrAbort
import util.Messages._
import util._

import scala.collection.mutable


object Coordinator {
  type DecisionLog = mutable.Map[Participant, (CommitOrAbort, Coordinator, String)]
  type BaPrepareLog = mutable.Set[Messages.BaPrepare]
  type BaCommitLog = mutable.Set[Messages.BaCommit]
  var coordinators: Set[Coordinator] = Set()
  var participants: Set[Participant] = Set()
  var stableStorage: mutable.Map[Transaction, mutable.Map[View, StableStorageItem]] = mutable.Map()

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
        participants.foreach(participant => participant ! Messages.Prepare(m.t, context.self))
        //Start byzantine agreement
        val v: View = 0 // TODO: is the view not given by the initiator?
        coordinators.foreach(coord => coord ! Messages.BaPrePrepare(v, m.t, CommitOrAbort.COMMIT, context.self))

      case m: CommitOutcome =>
        if (m.commitResult == CommitOrAbort.COMMIT) {
          context.log.info("Committed received from participant: " + m.from + ".Transaction: " + m.t)
        } else {
          context.log.info("Aborted received from participant: " + m.from + ".Transaction: " + m.t)
        }

      case InitViewChange(from: Coordinator) =>
      case m: NewView =>
      case m: BaPrepare =>
        //TODO: check message validity and only append valid messages (or append all and do validation in checkBaPrepare?)
        context.log.info("Received BaPrepare")
        val ss: StableStorageItem = stableStorage
          .getOrElseUpdate(m.t, mutable.Map())
          .getOrElseUpdate(m.v, new StableStorageItem())
        ss.baPrepareLog.add(m)
        if (ss.baState == BaState.UNKNOWN && ss.baPrepareLog.count(p => p.proposeCommit == m.proposeCommit) >= 2 * getF()) {
          //BaPrepared flag prevents duplicate messages
          //TODO: implement this in a way that functions for continuous operation instead of just one commit
          coordinators.foreach(coord => coord ! Messages.BaCommit(m.v, m.t, m.proposeCommit, context.self))
          ss.baState = BaState.PREPARED
          context.log.info("BaPrepared")
        }
      case m: BaCommit =>
        //TODO: check message validity and only append valid messages (or append all and do validation in checkBaCommit?)
        context.log.info("Received BaCommit")
        val ss: StableStorageItem = stableStorage
          .getOrElseUpdate(m.t, mutable.Map())
          .getOrElseUpdate(m.v, new StableStorageItem())
        ss.baCommitLog.add(m)
        if (ss.baState == BaState.PREPARED && ss.baCommitLog.count(p => p.proposeCommit == m.proposeCommit) >= 2 * getF()) {
          //BaCommitted flag prevents duplicate messages
          //TODO: implement this in a way that functions for continuous operation instead of just one message
          participants.foreach(part => part ! Messages.Commit(m.t, context.self, m.proposeCommit))
          ss.baState = BaState.COMMITTED
          context.log.info("BaCommitted")
        }
      case m: BaPrePrepare =>
        //TODO: implement message checking and handling the reject case (initiating view change)
        coordinators.foreach(coord => coord ! Messages.BaPrepare(m.v, m.t, m.proposeCommit, context.self))
        context.log.info("BaPrePrepared")

      case SendUnknownParticipants(participants: Set[Participant], from: Coordinator) =>
        this.participants |= participants

      case RequestUnknownParticipants(from: Coordinator) =>
        from ! Messages.SendUnknownParticipants(participants, context.self)

    }
    Behaviors.same
  }

  def getF(): Integer = {
    val f: Integer = (coordinators.size - 1) / 3
    f
  }

  class StableStorageItem() {
    val decisionLog: DecisionLog = mutable.Map()
    val baPrepareLog: BaPrepareLog = mutable.Set()
    val baCommitLog: BaCommitLog = mutable.Set()
    var baState: BaState = BaState.UNKNOWN
  }

  object BaState extends Enumeration {
    type BaState = Value
    val UNKNOWN, PREPARED, COMMITTED = Value
  }
}