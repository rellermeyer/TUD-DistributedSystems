package actors

import java.security.PublicKey

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import util.Messages._
import util._

import scala.collection.mutable


object Coordinator {
  def apply(factory: ActorContext[Signed[CoordinatorMessage]] => Behavior[Signed[CoordinatorMessage]]): Behavior[Signed[CoordinatorMessage]] = {
    Behaviors.logMessages(Behaviors.setup(factory))
  }

  import BaState.BaState

  class StableStorageItem() {
    val decisionLog: mutable.Map[ParticipantRef, (Decision, Coordinator, String)] = mutable.Map()
    val baPrePrepareLog: mutable.Set[Messages.BaPrePrepare] = mutable.Set()
    val baPrepareLog: mutable.Set[Messages.BaPrepare] = mutable.Set()
    val baCommitLog: mutable.Set[Messages.BaCommit] = mutable.Set()
    val registrationLog: mutable.Map[ParticipantRef, Messages.Register] = mutable.Map()
    val decisionCertificate: DecisionCertificate = mutable.Map()
    val participants: mutable.Set[ParticipantRef] = mutable.Set() // could be computed from signedRegistrations
    var v: View = 0
    var baState: BaState = BaState.INITIAL
    var digest: Digest = 0
    var timeOut_View: (View, Long) = (0, 0)
  }

  object BaState extends Enumeration {
    type BaState = Value
    val INITIAL, PREPARED, COMMITTED = Value
  }

}

class Coordinator(context: ActorContext[Signed[CoordinatorMessage]]
                  , keys: KeyTuple
                  , masterPubKey: PublicKey
                  , operational: Boolean
                  , byzantine: Boolean
                  , slow: Boolean) extends AbstractBehavior[Signed[CoordinatorMessage]](context) {

  import Coordinator._

  var coordinators: Array[Messages.CoordinatorRef] = Array(context.self)
  var i: Int = 0
  var f: Int = (coordinators.length - 1) / 3
  var stableStorage: mutable.Map[TransactionID, StableStorageItem] = mutable.Map()
  var timeOut = 500

  override def onMessage(message: Signed[CoordinatorMessage]): Behavior[Signed[CoordinatorMessage]] = {
    if (!message.verify(masterPubKey)) {
      context.log.warn("Message dropped, incorrect signature")
      return this
    }
    if (!operational) return this
    message.m match {
      case Setup(coordinators) =>
        if (slow) timeOut = 1
        this.coordinators = coordinators
        f = (coordinators.length - 1) / 3
        i = coordinators.indexOf(this.context.self)
      case m: Register =>
        val ss = stableStorage.getOrElseUpdate(m.t, new StableStorageItem())
        if (!ss.participants.contains(m.from)) {
          ss.participants += m.from
          ss.registrationLog(m.from) = m
          m.from ! ConfirmRegistration(m.t, m.from, context.self).sign(keys)
        }
        else {
          context.log.info("Messages.Register dropped, participant is already registered")
        }
      case m: VotePrepared =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            if (ss.participants.contains(m.from)) {
              m.vote match {
                case util.Messages.Decision.COMMIT =>
                  //TODO: create false decision certificate if byzantine
                  ss.decisionCertificate += (m.from -> DecisionCertificateEntry(ss.registrationLog(m.from), Option(m), None))
                  val isPrimary = i == ss.v % (3 * f + 1)
                  val enoughVotes = ss.decisionCertificate.size == ss.participants.size
                  if (isPrimary && enoughVotes) {
                    coordinators.foreach(coord => coord ! Messages.BaPrePrepare(ss.v, m.t, dec(Decision.COMMIT), ss.decisionCertificate, context.self).sign(keys))
                  }
                case util.Messages.Decision.ABORT =>
                  if (!ss.decisionCertificate.contains(m.from)) {
                    ss.decisionCertificate += (m.from -> DecisionCertificateEntry(ss.registrationLog(m.from), Option(m), None))
                    coordinators.foreach(coord => coord ! Messages.BaPrePrepare(ss.v, m.t, dec(Decision.ABORT), ss.decisionCertificate, context.self).sign(keys))
                  }
              }
            }
            else {
              context.log.warn("Voting participant is not registered")
            }
          case None =>
            context.log.error("Not implemented")
        }
      case m: InitCommit =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            ss.participants.foreach(p => p ! Messages.Prepare(m.t, context.self).sign(keys))
          case None =>
            context.log.error("not implemented")
        }
      case m: InitAbort =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            if (i == ss.v % (3 * f + 1)) { // primary
              //TODO: create false decision certificate if byzantine
              ss.decisionCertificate += (m.from -> DecisionCertificateEntry(ss.registrationLog(m.from), None, Option(m)))
              coordinators.foreach(coord => coord ! Messages.BaPrePrepare(ss.v, m.t, dec(Decision.ABORT), ss.decisionCertificate, context.self).sign(keys))
            }
          case None =>
            context.log.error("not implemented")
        }

      case m: ViewChange =>
        stableStorage.get(m.t) match {
          case Some(value) =>
            context.log.error("View change requested but not implemented yet.")
          case None =>
        }

      case m: BaPrePrepare =>

        stableStorage.get(m.t) match {
          case Some(value) =>
            //TODO: check if message is from primary
            //TODO: check if we are in the correct view
            if (!value.baPrePrepareLog.contains(m)) { // if no previous ba-pre-prepare message has been received
              var changeView = false
              m.o match {
                case util.Messages.Decision.COMMIT =>
                  value.participants.foreach(p => m.c.get(p) match {
                    case Some(part) =>
                      if ((part.registration.t != m.t) || (part.vote.get.vote != Decision.COMMIT)) { //check certificate
                        changeView = true
                        context.log.debug("invalid decision certificate")
                      }
                    case None =>
                      changeView = true
                      context.log.debug("locally known participant not in decision certificate")
                  }
                  )
                  if (!changeView) {
                    value.timeOut_View = (m.v, System.currentTimeMillis())
                    value.digest = m.c.hashCode()
                    context.log.debug("Digest:" + value.digest)
                    value.baPrePrepareLog += m
                    coordinators.foreach(coord => coord ! Messages.BaPrepare(m.v, m.t, value.digest, dec(Decision.COMMIT), context.self).sign(keys))
                  }
                case util.Messages.Decision.ABORT =>
                  //TODO: implement proper checks
                  value.timeOut_View = (m.v, System.currentTimeMillis())
                  value.digest = m.c.hashCode()
                  context.log.debug("Digest:" + value.digest)
                  value.baPrePrepareLog += m
                  coordinators.foreach(coord => coord ! Messages.BaPrepare(m.v, m.t, value.digest, dec(Decision.ABORT), context.self).sign(keys))
              }
              if (changeView) {
                val P = ViewChangeStateBaNotPrePrepared(m.v, m.t, value.decisionCertificate)
                coordinators.foreach(coord => coord ! ViewChange(m.v + 1, m.t, P, context.self).sign(keys)) // TODO: implement view change
                // TODO: abort?
              }
            }
          case None =>
        }
      case m: BaPrepare =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            var changeView = false

            if (ss.baState != BaState.INITIAL) {
              context.log.debug("not expecting BaPrepare")
              return this
            }
            if (m.c == ss.digest) { //check digest // TODO: not sure if this is how it should be done
              if (ss.baPrePrepareLog.exists(p => (p.o == m.o) && p.t == m.t)) { //check if same decision as in baPrePrepare
                ss.baPrepareLog += m
              } else {
                context.log.debug("proposed outcome different from BaPrePrepare stage")
                changeView = true
              }
            } else {
              context.log.debug("digest verification failed:" + m.c + " vs. " + ss.digest)
              changeView = true
            }
            if (ss.timeOut_View._1 != m.v || ((System.currentTimeMillis() - ss.timeOut_View._2) > timeOut)) {
              context.log.debug("Timeout or view verification failed. " + m.v + " == " + ss.timeOut_View._1 + " | " + System.currentTimeMillis() + " " + ss.timeOut_View._2)
              changeView = true
            }
            if (!changeView) {
              if (ss.baPrepareLog.count(p => p.o == m.o) >= 2 * f) {
                //BaPrepared flag prevents duplicate messages
                coordinators.foreach(coord => coord ! Messages.BaCommit(m.v, m.t, m.c, dec(m.o), context.self).sign(keys))
                ss.baState = BaState.PREPARED
                context.log.info("BaPrepared")
              }
            } else {
              context.log.debug("TimedOut or verification failed. Init view change.")
              val P = ViewChangeStateBaPrePrepared(m.v, m.t, m.o, ss.baPrePrepareLog.head.c)
              coordinators.foreach(coord => coord ! ViewChange(m.v + 1, m.t, P, context.self).sign(keys))
            }
          case None =>
        }
      case m: BaCommit =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            var changeView = false

            if (ss.baState != BaState.PREPARED) {
              context.log.debug("not expecting BaCommit")
              return this
            }
            ss.baCommitLog += m
            if (ss.timeOut_View._1 != m.v || ((System.currentTimeMillis() - ss.timeOut_View._2) > timeOut)) {
              changeView = true
            }
            if (!changeView) {
              if (byzantine) {
                if (m.o == Decision.ABORT) {
                  ss.participants.foreach(part => part ! Messages.Commit(m.t, context.self).sign(keys))
                  context.log.info("Byzantine BaCommitted")
                } else {
                  ss.participants.foreach(part => part ! Messages.Rollback(m.t, context.self).sign(keys))
                  context.log.info("Byzantine BaCommitted abort")
                }
              } else {
                if (m.o == Decision.COMMIT) {
                  if (ss.baCommitLog.count(p => p.o == m.o) >= 2 * f) {
                    ss.participants.foreach(part => part ! Messages.Commit(m.t, context.self).sign(keys))
                    ss.baState = BaState.COMMITTED // or just drop the transaction?
                    context.log.info("BaCommitted")
                  }
                }
                else {
                  if (ss.baCommitLog.count(p => p.o == m.o) >= 2 * f) {
                    ss.participants.foreach(part => part ! Messages.Rollback(m.t, context.self).sign(keys))
                    context.log.info("BaCommitted abort")
                  }
                }
              }
            } else {
              context.log.debug("TimedOut. Init view change.")
              val P = ViewChangeStateBaPrepared(m.v, m.t, m.o, ss.baPrePrepareLog.head.c, ss.baPrepareLog)
              coordinators.foreach(coord => coord ! ViewChange(m.v + 1, m.t, P, context.self).sign(keys))
            }
          case None =>
        }
      case Committed(t, commitResult, from) =>
    }
    this
  }

  def dec(d: Decision): Decision = {
    if (byzantine) {
      if (d == Decision.COMMIT) Decision.ABORT
      else Decision.COMMIT
    } else d
  }
}
