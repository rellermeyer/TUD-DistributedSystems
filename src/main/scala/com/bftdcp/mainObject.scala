package com.bftdcp

// Start writing your ScalaFiddle code here
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import Father.Command

object Coordinator {
  final case class PrepareRequest(whom: String) extends Command

  var participant : ActorRef[Command] = null

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case Father.Start(name,replyTo) =>
        println("Coordinator: Reveiced Start!")
        participant = replyTo

      case Participant.CommitRequest(whom) =>
        println("Coordinator: Received CommitRequest!")
        participant ! PrepareRequest("hi")

    }
    Behaviors.same
  }
}

object Participant {
  final case class CommitRequest(whom: String) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case Father.Start(name,replyTo) =>
        println("Participant: Reveiced Start!")
        replyTo ! CommitRequest("me")

      case Coordinator.PrepareRequest(whom) =>
        println("Participant: Received PrepareRequest!")
    }

    Behaviors.same
  }
}

object Father {
  sealed trait Command
  final case class Start(name: String, replyTo: ActorRef[Command]) extends Command
  final case class FatherStartMsg(cName: String, pName: String) extends Command

  def apply(): Behavior[FatherStartMsg] = Behaviors.setup { context =>
    //val participant = context.spawn(Participant(),"participant")

    println("I'm the father. Creating actors and sending start messages...")

    Behaviors.receiveMessage { message =>
      val coordRef = context.spawn(Coordinator(),message.cName)
      val partRef = context.spawn(Participant(),message.pName)

      partRef ! Start(message.pName,coordRef)
      coordRef ! Start(message.cName,partRef)

      Behaviors.same
    }
  }
}

object mainObject extends App {
  val father: ActorSystem[Father.FatherStartMsg] = ActorSystem(Father(),"FatherQuickStart")
  father ! Father.FatherStartMsg("CoordinatorName","ParticipantName")
}