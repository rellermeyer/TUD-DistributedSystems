package controllers

import actors.{Coordinator, Participant}
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.javadsl.Behaviors
import util.Messages
import util.Messages.{PropagateTransaction, Transaction}

object ActorStart {

  def apply(): Behavior[ActorStartMessage] = Behaviors.logMessages(Behaviors.setup { context =>
    context.getLog.info("Creating actors and sending start messages");

    Behaviors.receiveMessage { message =>

      // Create coordinators
      val coordinators: Array[Messages.Coordinator] = Array(
        context.spawn(Coordinator(), "Coordinator---1"),
        context.spawn(Coordinator(), "Coordinator---2"),
        context.spawn(Coordinator(), "Coordinator---3"),
        context.spawn(Coordinator(), "Coordinator---4")
      )

      // Send coordinators set of coordinators
      coordinators.foreach { x => x ! Messages.Setup(coordinators) }

      // Create participant(s)
      val participants: Set[Messages.Participant] = Set(
        context.spawn(Participant(coordinators), "PartInitiator-1")
      )


      // Let the participant start messaging the coordinator

      // send some transactions
      for (id <- 1 to 1) {
        val t = Transaction(id);
        for (x <- participants) {
          x ! PropagateTransaction(t)
        }
        coordinators.head ! Messages.InitCommit(t.id, participants.head)
        Thread.sleep(1000)
      }

      Behaviors.same
    }
  }

  final case class ActorStartMessage()
}

// This class is needed to run the application. It creates the principal actor and triggers it with his start message
object Start extends App {
  println("Starting")
  val actorStart: ActorSystem[ActorStart.ActorStartMessage] = ActorSystem(ActorStart(), "ActorStart")
  actorStart ! ActorStart.ActorStartMessage()
}