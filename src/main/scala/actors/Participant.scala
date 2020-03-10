package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import util._
import Messages._

object Participant {
  def apply(): Behavior[Messages.ParticipantMessage] = Behaviors.receive { (context, message) =>
    message match {
      case m: Greet =>
        context.log.info("Hello {}", m.whom)
        m.replyTo ! Greeted(m.whom, context.self)
      case Messages.Prepare(from: Coordinator) =>
      case Messages.Abort(from: Coordinator) =>
      case Messages.Commit(from: Coordinator) =>
      case m: InitCommit =>
      case m: InitAbort =>
      case message: Messages.InitiatorMessage =>
        message match {
          case Messages.RegisterWithInitiator(from: Participant) =>
        }
    }
    Behaviors.same
  }
}
