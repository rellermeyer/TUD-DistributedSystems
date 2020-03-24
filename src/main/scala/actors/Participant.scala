package actors

import actors.Participant.TransactionState.{ACTIVE, PREPARED, TransactionState}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import util.Messages._

import scala.collection.mutable


object Participant {
  def apply(coordinators: Array[Coordinator], decision: Decision): Behavior[ParticipantMessage] = {
    Behaviors.logMessages(Behaviors.setup(context => new FixedDecisionParticipant(context, coordinators, decision)))
  }

  class State(var s: TransactionState, val t: Transaction, val decisionLog: Array[Decision])

  object TransactionState extends Enumeration {
    type TransactionState = Value
    val ACTIVE, PREPARED = Value
  }

}

abstract class Participant(context: ActorContext[ParticipantMessage], coordinators: Array[Coordinator]) extends AbstractBehavior[ParticipantMessage](context) {

  import Participant._

  val f = (coordinators.length - 1) / 3
  val transactions: mutable.Map[TransactionID, State] = mutable.Map()

  override def onMessage(message: ParticipantMessage): Behavior[ParticipantMessage] = {
    message match {
      case m: Prepare =>
        transactions.get(m.t) match {
          case Some(s) =>
            prepare(m.t) match {
              case util.Messages.Decision.COMMIT =>
                s.s = PREPARED
                m.from ! VotePrepared(m.t, Decision.COMMIT, context.self)
              case util.Messages.Decision.ABORT =>
                // TODO: change into some aborted state?
                m.from ! VotePrepared(m.t, Decision.ABORT, context.self)
            }
          case None =>
            context.log.error("Transaction not known")
        }
      case m: Commit =>
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case PREPARED =>
              val coordinatorIndex = coordinators.indexOf(m.from)
              s.decisionLog(coordinatorIndex) = m.o
              if (s.decisionLog.count(x => x == m.o) >= f + 1) {
                // m.from ! Messages.Committed(null, m.o, context.self)
                m.o match {
                  case util.Messages.Decision.COMMIT =>
                    context.log.info("Committed transaction " + m.t)
                    transactions.remove(m.t);
                  case util.Messages.Decision.ABORT =>
                    context.log.info("Aborted transaction " + m.t)
                    transactions.remove(m.t);
                }
              }
              else {
                context.log.info("Waiting for more commits to make decision...")
              }
            case _ =>
          }
          case None =>
        }
      case m: PropagateTransaction =>
        transactions.get(m.t.id) match {
          case Some(s) =>
            context.log.warn("Transaction known, no action needed")
          case None =>
            transactions += (m.t.id -> new State(ACTIVE, m.t, new Array(coordinators.length)))
        }
        coordinators.foreach(c => c ! Register(m.t.id, context.self))
    }
    this
  }

  def prepare(t: TransactionID): Decision
}

class FixedDecisionParticipant(context: ActorContext[ParticipantMessage], coordinators: Array[Coordinator], decision: Decision) extends Participant(context, coordinators) {
  override def prepare(t: TransactionID): Decision = decision
}
