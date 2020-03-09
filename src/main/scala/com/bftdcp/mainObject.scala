package com.bftdcp

// Start writing your ScalaFiddle code here
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import Father.Command

object Messages {
  sealed trait Message
  /****** Patrick ******/
  //Initiator to Coordinator and Participant
  final case class initCommit() extends Message
  final case class initAbort() extends Message

  //Coordinator to Participant
  final case class prepare() extends Message
  final case class abort() extends Message
  final case class commit() extends Message

  /****** Miguel ******/
  //Coordinator to Initiator
  final case class commitSuccess() extends Message
  final case class abortSuccess() extends Message

  //Participant to Initiator
  final case class registerWithInitiator() extends Message //(page 39, start of second column seems to imply this is needed .Could perhaps be merged with the ack to initiateCommit / initiateAbort )

  //Coordinator to Coordinator
  final case class initViewChange() extends Message//(optional)
  /****** Douwe ******/
  final case class newView(newViewNumber,viewCertificate,transactionID, outcomeProposed,decisionCertificate) extends Message//(optional)
  final case class baPrepare(viewNumber,transactionID,decisionCertificate,outcomeProposed, coordinatorID) extends Message //decisionCertificate can be replaced with hash/digest 
  final case class baCommit(viewNumber,transactionID,decisionCertificate,outcomeProposed, coordinatorID) extends Message

  //Coordinator(primary) to Coordinator
  final case class baPrePrepare(viewNumber,transactionID,outcomeProposed,decisionCertificate) extends Message
  final case class sendUnknownParticipants(endPointReferences) extends Message// (see page 41, second whole paragraph)

  /****** Michael ******/
  //Coordinator to Coordinator(primary)
  final case class requestUnknownParticipants() extends Message

  //Participant to Coordinator
  final case class registerWithCoordinator() extends Message
  final case class prepared() extends Message
  final case class readOnly() extends Message
  final case class aborted() extends Message
  final case class committed() extends Message
}

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