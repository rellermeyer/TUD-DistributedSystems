package actors

import java.security.PublicKey

import actors.Participant.TransactionState.{ACTIVE, PREPARED, TransactionState}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import util.Messages._
import scala.collection.mutable


object Participant {
  def apply(coordinators: Array[Coordinator], decision: Decision, keyTuple: KeyTuple, masterPubKey: PublicKey): Behavior[Signed[ParticipantMessage]] = {
    Behaviors.logMessages(Behaviors.setup(context => new FixedDecisionParticipant(context, coordinators, decision, keyTuple, masterPubKey)))
  }

  type Participant = ActorRef[Signed[ParticipantMessage]]
  class State(var s: TransactionState, val t: Transaction, val decisionLog: Array[Decision],val registrations: mutable.Set[Coordinator], val initiator: Participant, val readyParticipants: mutable.Set[Participant])

  object TransactionState extends Enumeration {
    type TransactionState = Value
    val ACTIVE, PREPARED = Value
  }

}

abstract class Participant(context: ActorContext[Signed[ParticipantMessage]], coordinators: Array[Coordinator], keys: KeyTuple, masterPubKey: PublicKey) extends AbstractBehavior[Signed[ParticipantMessage]](context) {

  import Participant._

  val f: Int = (coordinators.length - 1) / 3
  val participants: Array[Participant.Participant] = Array(context.self)
  val transactions: mutable.Map[TransactionID, State] = mutable.Map()

  override def onMessage(message: Signed[ParticipantMessage]): Behavior[Signed[ParticipantMessage]] = {
    message.m match {
      case m: PropagateTransaction =>
        transactions.get(m.t.id) match {
          case Some(s) =>
            context.log.warn("Transaction known, no action needed")
          case None =>
            transactions += (m.t.id -> new State(ACTIVE, m.t, new Array(coordinators.length), mutable.Set().empty,m.from,mutable.Set().empty))
        }
        coordinators.foreach(c => c ! Register(m.t.id, context.self).sign(keys))
      case m: ConfirmRegistration =>
        if(message.verify(masterPubKey)) {
          transactions.get(m.t) match {
            case Some(s) =>
              if(!s.registrations.contains(m.from)) {
                s.registrations += m.from
              }
              if(s.registrations.size >= 2 * f + 1) {
                s.initiator ! PropagationReply(m.t, context.self).sign(keys)
              }
            case None =>
              context.log.error("Transaction not known")
          }
        }
      case m: PropagationReply =>
        if(message.verify(masterPubKey)) {
          transactions.get(m.t) match {
            case Some(s) =>
              if(!s.readyParticipants.contains(m.from)) {
                s.readyParticipants += m.from
              }
              if(s.readyParticipants.size == participants.size) {
                prepare(m.t) match {
                  case Decision.COMMIT =>
                    coordinators.foreach(c => c ! InitCommit(m.t, context.self).sign(keys))
                  case Decision.ABORT =>
                    coordinators.foreach(c => c ! InitAbort(m.t, context.self).sign(keys))
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
      case m: Rollback => {
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case _ => {
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
          }
          case None =>
        }
      }
    }
    this
  }

  def prepare(t: TransactionID): Decision

}

class FixedDecisionParticipant(context: ActorContext[Signed[ParticipantMessage]], coordinators: Array[Coordinator], decision: Decision, keyTuple: KeyTuple, masterPubKey: PublicKey) extends Participant(context, coordinators, keyTuple: KeyTuple, masterPubKey: PublicKey) {
  override def prepare(t: TransactionID): Decision = decision
}
