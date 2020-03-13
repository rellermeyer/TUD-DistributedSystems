package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import util._
import Messages._

object Participant {
  def apply(coordRef : ActorRef[CoordinatorMessage]): Behavior[Messages.ParticipantMessage] = {

    Behaviors.receive {(context, message) =>
    message match {
      case m: Greet =>
      context.log.info ("Hello {}", m.whom)
      m.replyTo ! Greeted (m.whom, context.self)
      case m : ParticipantStart =>   // This message is used to let the participant know when to start sending commit requests
        startWorkflow(m.partRef,coordRef)
      case Messages.Prepare (from: Coordinator) =>
        println ("Participant: prepare message received from "+from)
      case Messages.Abort (from: Coordinator) =>
      case Messages.Commit (from: Coordinator) =>
      case m: InitCommit =>
      case m: InitAbort =>
      case message: Messages.InitiatorMessage =>
      message match {
        case Messages.RegisterWithInitiator (from: Participant) =>
      }
  }
    Behaviors.same
  }
  }

  // Infinite loop which sends commitRequests every second
  def startWorkflow(partRef : ActorRef[ParticipantMessage],coordRef : ActorRef[CoordinatorMessage]): Unit = {
    while(true){
      Thread.sleep(1000)
      coordRef ! Messages.InitCommit(null,partRef)
    }
  }
}
