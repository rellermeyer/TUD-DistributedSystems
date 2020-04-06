package util

import actors.{Coordinator, FixedDecisionParticipant, Participant}
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import util.Messages.Decision

import scala.collection.mutable

abstract class Spawner(var uniqueID: Int = 0) {
  def apply(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0, nFailedCoordinators: Int = 0, nByzantinePrimaryCoord: Int = 0, nByzantineOtherCoord: Int = 0, nSlowCoord: Int = 0): (Array[Messages.CoordinatorRef], Array[Messages.ParticipantRef]) = {

    val (masterKey, kpg) = KeyPairGenerator()

    val cs = mutable.ArrayBuffer[Messages.CoordinatorRef]()
    for (_ <- 1 to nByzantinePrimaryCoord) {
      cs += spawn(Coordinator(new Coordinator(_, kpg(), masterKey, byzantine = true)), uniqueID + "Coordinator-" + cs.length)
    }
    for (_ <- 1 to nCoordinators) {
      cs += spawn(Coordinator(new Coordinator(_, kpg(), masterKey)), uniqueID + "Coordinator-" + cs.length)
    }
    for (_ <- 1 to nFailedCoordinators) {
      cs += spawn(Coordinator(new Coordinator(_, kpg(), masterKey, operational = false)), uniqueID + "Coordinator-" + cs.length)
    }
    for (_ <- 1 to nByzantineOtherCoord) {
      cs += spawn(Coordinator(new Coordinator(_, kpg(), masterKey, byzantine = true)), uniqueID + "Coordinator-" + cs.length)
    }
    for (_ <- 1 to nSlowCoord) {
      cs += spawn(Coordinator(new Coordinator(_, kpg(), masterKey, slow = true)), uniqueID + "Coordinator-" + cs.length)
    }
    val csArray = cs.toArray

    val setup = Messages.Setup(csArray).sign(kpg())
    cs.foreach { c => c ! setup }

    val ps = mutable.ArrayBuffer[Messages.ParticipantRef]()
    for (_ <- 1 to nCommittingParticipants) {
      ps += spawn(Participant(new FixedDecisionParticipant(_, csArray, Decision.COMMIT, kpg(), masterKey)), uniqueID + "Participant-" + ps.length)
    }
    for (_ <- 1 to nAbortingParticipants) {
      ps += spawn(Participant(new FixedDecisionParticipant(_, csArray, Decision.ABORT, kpg(), masterKey)), uniqueID + "Participant-" + ps.length)
    }
    val psArray = ps.toArray
    uniqueID += 1
    (csArray, psArray)
  }

  def spawn[T](behavior: Behavior[T], name: String): ActorRef[T]
}

class SpawnerImpl[T](context: ActorContext[T]) extends Spawner() {
  override def spawn[K](behavior: Behavior[K], name: String): ActorRef[K] = context.spawn(behavior, name)
}
