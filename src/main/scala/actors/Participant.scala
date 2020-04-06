package actors

import java.security.PublicKey

import actors.Participant.TransactionState.{ACTIVE, PREPARED, TransactionState}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import util.Messages._

import scala.collection.mutable


object Participant {
  def apply(factory: ActorContext[Signed[ParticipantMessage]] => Behavior[Signed[ParticipantMessage]]): Behavior[Signed[ParticipantMessage]] = {
    Behaviors.logMessages(Behaviors.setup(factory))
  }

  //TODO: change readyParticpants to a mapping (particpants, ready)
  //TODO: use knowledge of other participants to check whether received message is from a known participant
  class State(var s: TransactionState
              , val t: Transaction
              , val decisionLog: Array[Decision]
              , val registrations: mutable.Set[CoordinatorRef]
              , val initiator: ParticipantRef
              , val participants: Array[ParticipantRef]
              , val readyParticipants: mutable.Set[ParticipantRef]
              , val initAction: Decision)

  object TransactionState extends Enumeration {
    type TransactionState = Value
    val ACTIVE, PREPARED = Value
  }

}

abstract class Participant(context: ActorContext[Signed[ParticipantMessage]]
                           , coordinators: Array[CoordinatorRef]
                           , keys: KeyTuple
                           , masterPubKey: PublicKey) extends AbstractBehavior[Signed[ParticipantMessage]](context) {

  import Participant._

  val f: Int = (coordinators.length - 1) / 3
  val transactions: mutable.Map[TransactionID, State] = mutable.Map()

  override def onMessage(message: Signed[ParticipantMessage]): Behavior[Signed[ParticipantMessage]] = {
    message.m match {
      case m: AppointInitiator =>
        transactions += (m.t.id -> new State(ACTIVE, m.t, new Array(coordinators.length), mutable.Set().empty, m.from, m.participants, mutable.Set().empty, m.initAction))
        val propagate = Propagate(m.t, context.self).sign(keys)
        m.participants.foreach(_ ! propagate)
      case m: Propagate =>
        if (message.verify(masterPubKey)) {
          transactions.get(m.t.id) match {
            case Some(s) =>
              context.log.debug("Transaction already known")
            case None =>
              transactions += (m.t.id -> new State(ACTIVE, m.t, new Array(coordinators.length), mutable.Set().empty, m.from, Array.empty, mutable.Set().empty, null))
          }
        }
        val register = Register(m.t.id, context.self).sign(keys)
        coordinators.foreach(_ ! register)
      case m: Registered =>
        if (message.verify(masterPubKey)) {
          transactions.get(m.t) match {
            case Some(s) =>
              if (!s.registrations.contains(m.from)) {
                s.registrations += m.from
              }
              if (s.registrations.size >= 2 * f + 1) {
                s.initiator ! Propagated(m.t, context.self).sign(keys)
              }
            case None =>
              context.log.error("Transaction not known")
          }
        }
      case m: Propagated =>
        if (message.verify(masterPubKey)) {
          transactions.get(m.t) match {
            case Some(s) =>
              if (!s.readyParticipants.contains(m.from)) {
                s.readyParticipants += m.from
                if (s.readyParticipants.size == s.participants.size) {
                  s.initAction match {
                    case Decision.COMMIT =>
                      val initCommit = InitCommit(m.t, context.self).sign(keys)
                      coordinators.foreach(_ ! initCommit)
                    case Decision.ABORT =>
                      val initAbort = InitAbort(m.t, context.self).sign(keys)
                      coordinators.foreach(_ ! initAbort)
                  }
                }
              }
            //TODO: if some timeout has passed, send initAbort instead
            case None =>
              context.log.error("Transaction not known")
          }
        } else {
          context.log.error("Incorrect signature")
        }
      case m: Prepare =>
        if (message.verify(masterPubKey)) {
          transactions.get(m.t) match {
            case Some(s) =>
              prepare(m.t) match {
                case util.Messages.Decision.COMMIT =>
                  s.s = PREPARED
                  m.from ! VotePrepared(m.t, Decision.COMMIT, context.self).sign(keys)
                case util.Messages.Decision.ABORT =>
                  m.from ! VotePrepared(m.t, Decision.ABORT, context.self).sign(keys)
              }
            case None =>
              m.from ! VotePrepared(m.t, Decision.ABORT, context.self).sign(keys)
              context.log.error("Transaction not known")
          }
        } else {
          context.log.error("Incorrect signature")
        }
      case m: Commit =>
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case PREPARED =>
              if (message.verify(masterPubKey)) {
                val coordinatorIndex = coordinators.indexOf(m.from)
                s.decisionLog(coordinatorIndex) = Decision.COMMIT
              } else {
                context.log.error("Incorrect signature")
              }
            case _ =>
              context.log.error(s"unexpected state, should be PREPARED instead of {s.s}")
          }
            if (s.decisionLog.count(x => x == Decision.COMMIT) >= f + 1) {
              // m.from ! Messages.Committed(null, m.o, context.self)
              context.log.info("Committed transaction " + m.t)
              transactions.remove(m.t)
            }
            else {
              context.log.info("Waiting for more commits to make decision...")
            }
          case None =>
        }
      case m: Rollback =>
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case _ =>
              if (message.verify(masterPubKey)) {
                val coordinatorIndex = coordinators.indexOf(m.from)
                s.decisionLog(coordinatorIndex) = Decision.ABORT
                if (s.decisionLog.count(x => x == Decision.ABORT) >= f + 1) {
                  context.log.info("Aborted transaction " + m.t)
                  transactions.remove(m.t)
                }
              } else {
                context.log.error("Incorrect signature")
              }
          }
          case None =>
        }
    }
    this
  }

  def prepare(t: TransactionID): Decision

}

class FixedDecisionParticipant(context: ActorContext[Signed[ParticipantMessage]], coordinators: Array[CoordinatorRef], decision: Decision, keyTuple: KeyTuple, masterPubKey: PublicKey) extends Participant(context, coordinators, keyTuple: KeyTuple, masterPubKey: PublicKey) {
  override def prepare(t: TransactionID): Decision = decision
}
