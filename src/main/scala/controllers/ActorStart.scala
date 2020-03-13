package controllers

import actors.{Coordinator, Participant}
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import util.Messages

object ActorStart {
  // To be implemented later
  // focus on tests for now...
  final case class ActorStartMessage()

  def apply(): Behavior[ActorStartMessage] = Behaviors.setup { context =>

    println("ActorStart: I'm the ActorStart object. Creating actors and sending start messages...")

    Behaviors.receiveMessage { message =>

      //Create coordinator
      val coordRef = context.spawn(Coordinator(),"CoordinatorObjectName")
      //Create participant
      val partRef = context.spawn(Participant(coordRef), "ParticipantObjectName")

      // Let the participant start messaging the coordinator
      partRef ! Messages.ParticipantStart(partRef)

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