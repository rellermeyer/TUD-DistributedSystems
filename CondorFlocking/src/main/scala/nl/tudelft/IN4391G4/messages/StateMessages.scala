package nl.tudelft.IN4391G4.messages

import akka.actor.ActorRef

object StateMessages {

  trait StateMessage

  final case class RequestStates() extends StateMessage
  final case class StateUpdate(state: MachineState) extends StateMessage
  final case class StateUpdates(states: Map[ActorRef, MachineState]) extends StateMessage
}
