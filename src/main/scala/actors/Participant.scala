package actors

import actors.Participant.TransactionState.{ACTIVE, PREPARED, TransactionState}
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import java.security.{PrivateKey, PublicKey}

import util.Messages
import util.Messages._

import scala.collection.mutable


object Participant {
  def apply(coordinators: Array[Coordinator], decision: Decision, keyTuple: KeyTuple, masterPubKey: PublicKey): Behavior[ParticipantMessage] = {
    Behaviors.logMessages(Behaviors.setup(context => new FixedDecisionParticipant(context, coordinators, decision, keyTuple, masterPubKey)))
  }

  class State(var s: TransactionState, val t: Transaction, val decisionLog: Array[Decision])

  object TransactionState extends Enumeration {
    type TransactionState = Value
    val ACTIVE, PREPARED = Value
  }

}

abstract class Participant(context: ActorContext[ParticipantMessage], coordinators: Array[Coordinator], keyTuple: KeyTuple, masterPubKey: PublicKey) extends AbstractBehavior[ParticipantMessage](context) {

  import Participant._

  val privateKey = keyTuple._1
  val signedPublicKey = keyTuple._2
  val f = (coordinators.length - 1) / 3
  val transactions: mutable.Map[TransactionID, State] = mutable.Map()

  override def onMessage(message: ParticipantMessage): Behavior[ParticipantMessage] = {
    message match {
      case m: Prepare =>
        if (verify(m.t.toString + m.from.toString, m.s, masterPubKey)) {
          transactions.get(m.t) match {
            case Some(s) =>
              prepare(m.t) match {
                case util.Messages.Decision.COMMIT =>
                  s.s = PREPARED
                  m.from ! VotePrepared(m.t, Decision.COMMIT, sign(m.t.toString() + Decision.COMMIT.toString + context.self.toString), context.self)
                case util.Messages.Decision.ABORT =>
                  // TODO: change into some aborted state?
                  m.from ! VotePrepared(m.t, Decision.ABORT, sign(m.t.toString() + Decision.ABORT.toString + context.self.toString), context.self)
              }
            case None =>
              m.from ! VotePrepared(m.t, Decision.ABORT, sign(m.t.toString), context.self)
              context.log.error("Transaction not known")
          }
        } else {
          context.log.error("Incorrect signature")
        }
      case m: Commit =>
        transactions.get(m.t) match {
          case Some(s) => s.s match {
            case PREPARED =>
              val coordinatorIndex = coordinators.indexOf(m.from)
              s.decisionLog(coordinatorIndex) = Decision.COMMIT
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
              val coordinatorIndex = coordinators.indexOf(m.from)
              s.decisionLog(coordinatorIndex) = Decision.ABORT
              if (s.decisionLog.count(x => x == Decision.ABORT) >= f + 1) {
                context.log.info("Aborted transaction " + m.t)
                transactions.remove(m.t)
              }
            }
            case _ =>
          }
          case None =>
        }
      }

      case m: PropagateTransaction =>
        transactions.get(m.t.id) match {
          case Some(s) =>
            context.log.warn("Transaction known, no action needed")
          case None =>
            transactions += (m.t.id -> new State(ACTIVE, m.t, new Array(coordinators.length)))
        }
        coordinators.foreach(c => c ! Register(m.t.id, sign(m.t.id.toString() + context.self.toString), context.self))
    }
    this
  }


  def sign(data: String): SignatureTuple ={
    return Messages.sign(data, privateKey,signedPublicKey)
  }

  def prepare(t: TransactionID): Decision

}

class FixedDecisionParticipant(context: ActorContext[ParticipantMessage], coordinators: Array[Coordinator], decision: Decision, keyTuple: KeyTuple, masterPubKey: PublicKey) extends Participant(context, coordinators, keyTuple: KeyTuple, masterPubKey: PublicKey) {
  override def prepare(t: TransactionID): Decision = decision
}
