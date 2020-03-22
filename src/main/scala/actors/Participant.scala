package actors

import actors.Participant.TransactionState.{COMMITTED, NEW, PREPARED, TransactionState}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import util.Messages._

import scala.collection.mutable


object Participant {
  def apply(coordinators: Array[Coordinator]): Behavior[ParticipantMessage] = {
    Behaviors.logMessages(Behaviors.setup(context => new Participant(context, coordinators)))
  }

  class State(val t: Transaction, var s: TransactionState, val decisionLog: Array[Decision])

  object TransactionState extends Enumeration {
    type TransactionState = Value
    val NEW, PREPARED, COMMITTED = Value
  }

}

class Participant(context: ActorContext[ParticipantMessage], coordinators: Array[Coordinator]) extends AbstractBehavior[ParticipantMessage](context) {

  import Participant._

  val f = (coordinators.length - 1) / 3
  val transactions: mutable.Map[TransactionID, State] = mutable.Map()

  override def onMessage(message: ParticipantMessage): Behavior[ParticipantMessage] = {
    message match {
      case m: Prepare =>
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case NEW =>
              s.s = PREPARED
              m.from ! VotePrepared(m.t, Decision.COMMIT, context.self)
            case _ =>
              context.log.error("Transaction not in NEW state")
          }
          case None =>
            context.log.error("Transaction not known")
        }
      case m: Commit =>
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case NEW =>
            case PREPARED =>
              // TODO: add decisionLog
              val coordinatorIndex = coordinators.indexOf(m.from)
              s.decisionLog(coordinatorIndex) = m.o
              if (s.decisionLog.count(x => x == m.o) > f + 1) {
                // m.from ! Messages.Committed(null, m.o, context.self)
                m.o match {
                  case util.Messages.Decision.COMMIT =>
                    context.log.info("Committed transaction " + m.t)
                  case util.Messages.Decision.ABORT =>
                    context.log.info("Aborted transaction " + m.t)
                }
              }
              else {
                context.log.info("Waiting for more commits to make decision...")
              }
            case COMMITTED =>
          }
          case None =>
        }
      case m: PropagateTransaction =>
        // TODO: check if already in there
        transactions += (m.t.id -> new State(m.t, NEW, new Array(coordinators.length)))
        coordinators.foreach(c => c ! Register(m.t.id, context.self))
    }
    this
  }
}
