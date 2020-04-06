package controllers

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import util.Messages.{AppointInitiator, Decision, Transaction}
import util.SpawnerImpl


object ActorStart {

  def apply(): Behavior[ActorStartMessage] = Behaviors.logMessages(Behaviors.setup { context =>
    context.log.info("Creating actors and sending start messages");

    Behaviors.receiveMessage { _ =>

      val spawner = new SpawnerImpl(context)
      val (coordinators, participants) = spawner(nCoordinators = 4, nCommittingParticipants = 1)

      // send some transactions
      val numberOfTransactions = 100
      val initiator = participants.head
      for (id <- 0 until numberOfTransactions) {
        initiator ! AppointInitiator(Transaction(id), Decision.COMMIT, participants, initiator).fakesign()
      }
      Behaviors.same
    }
  })

  final case class ActorStartMessage()

}

// This class is needed to run the application. It creates the principal actor and triggers it with his start message
object Start extends App {
  println("Starting")
  val actorStart: ActorSystem[ActorStart.ActorStartMessage] = ActorSystem(ActorStart(), "ActorStart")
  actorStart ! ActorStart.ActorStartMessage()
}