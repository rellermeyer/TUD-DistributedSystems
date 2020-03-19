package controllers

import actors.{Coordinator, Participant}
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import util.Messages
import util.Messages.{Coordinator, Participant}

object ActorStart {
  // To be implemented later
  // focus on tests for now...
  final case class ActorStartMessage()

  def apply(): Behavior[ActorStartMessage] = Behaviors.setup { context =>
    context.getLog.info("Creating actors and sending start messages");

    Behaviors.receiveMessage { message =>

      // Create coordinators
      val coordinators: Set[Coordinator] = Set(
        context.spawn(Coordinator(), "Coordinator---1"),
        context.spawn(Coordinator(), "Coordinator---2"),
        context.spawn(Coordinator(), "Coordinator---3"),
        context.spawn(Coordinator(), "Coordinator---4")
      )

      // Send coordinators set of coordinators
      coordinators.foreach { x => x ! Messages.SendCoordinatorSet(coordinators)}
      // TODO: avoid coordinators registering with themselves

      // Create participant(s)
      val participants: Set[Participant] = Set(
        context.spawn(Participant(coordinators.head), "PartInitiator-1")
      )

      // Let the participant start messaging the coordinator
      val initiator = participants.head;
      initiator ! Messages.ParticipantStart()

      Behaviors.same
    }
  }
}

// This class is needed to run the application. It creates the principal actor and triggers it with his start message
object Start extends App {
  println("Starting")
  val actorStart: ActorSystem[ActorStart.ActorStartMessage] = ActorSystem(ActorStart(),"ActorStart")
  actorStart ! ActorStart.ActorStartMessage()
}