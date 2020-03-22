package actors

import actors.Coordinator.BaState.BaState
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import util.Messages.Decision.Decision
import util.Messages._
import util._

import scala.collection.mutable


object Coordinator {
  def apply(): Behavior[CoordinatorMessage] = {
    Behaviors.logMessages(Behaviors.setup(context => new Coordinator(context)))
  }

  class StableStorageItem() {
    val decisionLog: mutable.Map[Participant, (Decision, Coordinator, String)] = mutable.Map()
    val baPrepareLog: mutable.Set[Messages.BaPrepare] = mutable.Set()
    val baCommitLog: mutable.Set[Messages.BaCommit] = mutable.Set()
    val decisionCertificate: DecisionCertificate = mutable.Map()
    val participants: mutable.Set[Participant] = mutable.Set() // could be computed from signedRegistrations
    var v: View = 0
    var baState: BaState = BaState.INITIAL
  }

  object BaState extends Enumeration {
    type BaState = Value
    val INITIAL, PREPARED, COMMITTED = Value
  }

}

class Coordinator(context: ActorContext[CoordinatorMessage]) extends AbstractBehavior[CoordinatorMessage](context) {

  import Coordinator._

  var coordinators: Array[Messages.Coordinator] = Array(context.self)
  var i = 0
  var f: Int = (coordinators.length - 1) / 3
  var stableStorage: mutable.Map[TransactionID, StableStorageItem] = mutable.Map()

  override def onMessage(message: CoordinatorMessage): Behavior[CoordinatorMessage] = {
    message match {
      case Setup(coordinators) =>
        this.coordinators = coordinators
        f = (coordinators.length - 1) / 3
        i = coordinators.indexOf(this.context.self)
      case m: Register =>
        val ss = stableStorage.getOrElseUpdate(m.t, new StableStorageItem())
        if (!ss.participants.contains(m.from)) {
          ss.decisionCertificate += (m.from -> DecisionCertificateEntry(m, Option.empty))
          ss.participants += m.from
        }
        else {

        }
      case m: VotePrepared =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            ss.decisionCertificate.get(m.from) match {
              case Some(value) =>
                value.vote match {
                  case Some(value) =>
                  case None =>
                    // TODO check if *everyone* voted *correct*! COMMIT(or also abort)?
                    if (i == ss.v % (3 * f + 1)) { // primary
                      coordinators.foreach(coord => coord ! Messages.BaPrePrepare(ss.v, m.t, Decision.COMMIT, ss.decisionCertificate, context.self))
                    }
                }
              case None =>
            }
          case None =>
            context.log.error("Not implemented")
        }
      case Committed(t, commitResult, from) =>
      case m: InitCommit =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            ss.participants.foreach(p => p ! Messages.Prepare(m.t, context.self))
          // TODO: spread to replicas?
          case None =>
            context.log.error("not implemented")
        }
      case m: ViewChange =>
      case m: BaPrepare =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            if (ss.baState != BaState.INITIAL) {
              // TODO: simply ignore?
              return this
            }
            ss.baPrepareLog += m
            // TODO: check if all are for the same digest
            // TODO: is checking for o really necessary?
            // TODO: make check more advanced
            if (ss.baPrepareLog.count(p => p.o == m.o) >= 2 * f) {
              //BaPrepared flag prevents duplicate messages
              val decisionCertDigest = 0
              coordinators.foreach(coord => coord ! Messages.BaCommit(m.v, m.t, decisionCertDigest, m.o, context.self))
              ss.baState = BaState.PREPARED
              context.log.info("BaPrepared")
            }
            else {

            }
          case None =>
        }
      case m: BaCommit =>
        stableStorage.get(m.t) match {
          case Some(ss) =>
            if (ss.baState != BaState.PREPARED) {
              // TODO: simply ignore?
              return this
            }
            ss.baCommitLog += m
            // TODO: make the check more advanced
            if (ss.baCommitLog.count(p => p.o == m.o) >= 2 * f) {
              ss.participants.foreach(part => part ! Messages.Commit(m.t, m.o, context.self))
              ss.baState = BaState.COMMITTED // or just drop the transaction?
              context.log.info("BaCommitted")
            }
            else {

            }
          case None =>
        }
      case m: BaPrePrepare =>
        stableStorage.get(m.t) match {
          case Some(value) =>
            // TODO: do all the checks (is he really primary, is the data correctl, are the registrations a superset,...)
            // TODO: generate digest from m.c
            val digest = 0
            coordinators.foreach(coord => coord ! Messages.BaPrepare(m.v, m.t, digest, m.o, context.self))
          case None =>
        }
    }
    this
  }

}