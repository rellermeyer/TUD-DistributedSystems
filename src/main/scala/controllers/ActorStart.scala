package controllers

import java.security.{KeyPair, KeyPairGenerator, PrivateKey, PublicKey}

import actors.{Coordinator, Participant}
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import util.Messages
import util.Messages.{Decision, PropagateTransaction, SignedPublicKey, Transaction, sign}


object ActorStart {

  def genSignedKey(kpg: KeyPairGenerator, masterKey: KeyPair): (PrivateKey, SignedPublicKey) = {
    var keyPair = kpg.generateKeyPair
    return (keyPair.getPrivate, (keyPair.getPublic, sign(keyPair.getPublic.toString, masterKey.getPrivate)))
  }
  def apply(): Behavior[ActorStartMessage] = Behaviors.logMessages(Behaviors.setup { context =>
    context.getLog.info("Creating actors and sending start messages");

    Behaviors.receiveMessage { message =>

      var kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
      kpg.initialize(2048)
      var masterKey = kpg.generateKeyPair

      // Create coordinators
      val coordinators: Array[Messages.Coordinator] = Array(
        context.spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(),operational = true, byzantine = false), "Coordinator---1"),
        context.spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(),operational = true, byzantine = false), "Coordinator---2"),
        context.spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(),operational = true, byzantine = false), "Coordinator---3"),
        context.spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(),operational = true, byzantine = false), "Coordinator---4")
      )

      // Send coordinators set of coordinators
      coordinators.foreach { x => x ! Messages.Setup(coordinators) }

      // Create participant(s)
      val participants: Set[Messages.Participant] = Set(
        context.spawn(Participant(coordinators, Decision.COMMIT, genSignedKey(kpg, masterKey), masterKey.getPublic()), "PartInitiator-1")
      )


      // Let the participant start messaging the coordinator

      // send some transactions
      // - propagate
      val numberOfTransactions = 100
      val transactions = new Array[Transaction](numberOfTransactions)
      for (id <- 0 until numberOfTransactions) {
        transactions(id) = Transaction(id);
        for (x <- participants) {
          x ! PropagateTransaction(transactions(id))
        }
      }
      Thread.sleep(1000)
      // - start the distributed commit
      for (id <- 0 until numberOfTransactions) {
        coordinators.foreach(c => c ! Messages.InitCommit(transactions(id).id, participants.head))
        Thread.sleep(500)
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