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
                  , operational: Boolean = true
                  , byzantine: Boolean = false
                  , slow: Boolean = false) extends AbstractBehavior[Signed[CoordinatorMessage]](context) {

  import Coordinator._

  var coordinators: Array[Messages.CoordinatorRef] = Array(context.self)
  var i: Int = 0
  var f: Int = (coordinators.length - 1) / 3
  var stableStorage: mutable.Map[TransactionID, StableStorageItem] = mutable.Map()
  var timeOut = 1000

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
          m.from ! Registered(m.t, m.from, context.self).sign(keys)
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
                    val decision = changeDecisionIfByzantine(Decision.COMMIT)
                    val baPrePrepare = Messages.BaPrePrepare(ss.v, m.t, decision, ss.decisionCertificate.clone, context.self).sign(keys)
                    coordinators.foreach(_ ! baPrePrepare)
                  }
                case util.Messages.Decision.ABORT =>
                  if (!ss.decisionCertificate.contains(m.from)) {
                    ss.decisionCertificate += (m.from -> DecisionCertificateEntry(ss.registrationLog(m.from), Option(m), None))
                    val decision = changeDecisionIfByzantine(Decision.ABORT)
                    val baPrePrepare = Messages.BaPrePrepare(ss.v, m.t, decision, ss.decisionCertificate.clone, context.self).sign(keys)
                    coordinators.foreach(_ ! baPrePrepare)
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
            val prepare = Messages.Prepare(m.t, context.self).sign(keys)
            ss.participants.foreach(_ ! prepare)
          case None =>
            context.log.error("not implemented")
        }
      case m: InitAbort =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            if (i == ss.v % (3 * f + 1)) { // primary
              //TODO: create false decision certificate if byzantine
              ss.decisionCertificate += (m.from -> DecisionCertificateEntry(ss.registrationLog(m.from), None, Option(m)))
              val decision = changeDecisionIfByzantine(Decision.ABORT)
              val baPrePrepare = Messages.BaPrePrepare(ss.v, m.t, decision, ss.decisionCertificate.clone, context.self).sign(keys)
              coordinators.foreach(_ ! baPrePrepare)
            }
          case None =>
            context.log.error("not implemented")
        }

      case m: ViewChange =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            context.log.error("View change requested but not implemented yet.")
          case None =>
        }

      case m: BaPrePrepare =>

        stableStorage.get(m.t) match {
          case Some(ss) =>
            //TODO: check if message is from primary
            //TODO: check if we are in the correct view
            if (!ss.baPrePrepareLog.contains(m)) { // if no previous ba-pre-prepare message has been received
              var changeView = false
              m.o match {
                case util.Messages.Decision.COMMIT =>
                  ss.participants.foreach(p => m.c.get(p) match {
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
                    ss.timeOut_View = (m.v, System.currentTimeMillis())
                    ss.digest = m.c.hashCode()
                    ss.baPrePrepareLog += m
                    val decision = changeDecisionIfByzantine(Decision.COMMIT)
                    val baPrepare = Messages.BaPrepare(m.v, m.t, ss.digest, decision, context.self).sign(keys)
                    coordinators.foreach(_ ! baPrepare)
                  }
                case util.Messages.Decision.ABORT =>
                  //TODO: implement proper checks
                  ss.timeOut_View = (m.v, System.currentTimeMillis())
                  ss.digest = m.c.hashCode()
                  context.log.debug("Digest:" + ss.digest)
                  ss.baPrePrepareLog += m
                  val decision = changeDecisionIfByzantine(Decision.ABORT)
                  val baPrepare = Messages.BaPrepare(m.v, m.t, ss.digest, decision, context.self).sign(keys)
                  coordinators.foreach(_ ! baPrepare)
              }
              if (changeView) {
                val P = ViewChangeStateBaNotPrePrepared(m.v, m.t, ss.decisionCertificate)
                val viewChange = ViewChange(m.v + 1, m.t, P, context.self).sign(keys)
                coordinators.foreach(_ ! viewChange) // TODO: implement view change
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
                val decision = changeDecisionIfByzantine(m.o)
                val baCommit = Messages.BaCommit(m.v, m.t, m.c, decision, context.self).sign(keys)
                coordinators.foreach(_ ! baCommit)
                ss.baState = BaState.PREPARED
                context.log.info("BaPrepared")
              }
            } else {
              context.log.debug("TimedOut or verification failed. Init view change.")
              val P = ViewChangeStateBaPrePrepared(m.v, m.t, m.o, ss.baPrePrepareLog.head.c)
              val viewChange = ViewChange(m.v + 1, m.t, P, context.self).sign(keys)
              coordinators.foreach(_ ! viewChange)
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
                  val commit = Messages.Commit(m.t, context.self).sign(keys)
                  ss.participants.foreach(_ ! commit)
                  context.log.info("Byzantine BaCommitted")
                } else {
                  val rollback = Messages.Rollback(m.t, context.self).sign(keys)
                  ss.participants.foreach(_ ! rollback)
                  context.log.info("Byzantine BaCommitted abort")
                }
              } else {
                if (m.o == Decision.COMMIT) {
                  if (ss.baCommitLog.count(p => p.o == m.o) >= 2 * f) {
                    val commit = Messages.Commit(m.t, context.self).sign(keys)
                    ss.participants.foreach(_ ! commit)
                    ss.baState = BaState.COMMITTED // or just drop the transaction?
                    context.log.info("BaCommitted")
                  }
                }
                else {
                  if (ss.baCommitLog.count(p => p.o == m.o) >= 2 * f) {
                    val rollback = Messages.Rollback(m.t, context.self).sign(keys)
                    ss.participants.foreach(_ ! rollback)
                    context.log.info("BaCommitted abort")
                  }
                }
              }
            } else {
              context.log.debug("TimedOut. Init view change.")
              val P = ViewChangeStateBaPrepared(m.v, m.t, m.o, ss.baPrePrepareLog.head.c, ss.baPrepareLog)
              val viewChange = ViewChange(m.v + 1, m.t, P, context.self).sign(keys)
              coordinators.foreach(_ ! viewChange)
            }
          case None =>
        }
      case Committed(t, commitResult, from) =>
    }
    this
  }

  def changeDecisionIfByzantine(decision: Decision): Decision = {
    if (byzantine) {
      decision match {
        case Decision.COMMIT => Decision.ABORT
        case Decision.ABORT => Decision.COMMIT
      }
    } else {
      decision
    }
  }
}
