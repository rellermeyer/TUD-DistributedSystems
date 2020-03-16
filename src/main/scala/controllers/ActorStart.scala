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

    println("ActorStart: I'm the ActorStart object. Creating actors and sending start messages...")

    Behaviors.receiveMessage { message =>
      var coordinators: Set[Coordinator] = Set()
      var participants: Set[Participant] = Set()

      //Create coordinator
      val coordRef = context.spawn(Coordinator(),"CoordinatorObjectName")
      val coordRef1 = context.spawn(Coordinator(),"CoordinatorObjectName1")
      val coordRef2 = context.spawn(Coordinator(),"CoordinatorObjectName2")
      val coordRef3 = context.spawn(Coordinator(),"CoordinatorObjectName3")
      coordinators += coordRef
      coordinators += coordRef1
      coordinators += coordRef2
      coordinators += coordRef3

      //Create participant
      val partRef = context.spawn(Participant(coordRef), "ParticipantObjectName")
      participants += partRef

      // Send coordinators set of coordinators
      coordinators.foreach { x => x ! Messages.SendCoordinatorSet(coordinators)}

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