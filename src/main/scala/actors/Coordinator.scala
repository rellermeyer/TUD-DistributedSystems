package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import util._
import Messages._

object Coordinator {
  var participants: Set[Participant] = Set()
  def apply(): Behavior[CoordinatorMessage] = Behaviors.receive { (context, message) =>
    message match {
      case m: Greet =>
        context.log.info("Hello {}", m.whom)
        m.replyTo ! Greeted(m.whom, context.self)
      case RegisterWithCoordinator(from: Participant) =>
        participants += from
      case Prepared(t: Transaction, from: Participant) =>
      case Aborted(t: Transaction, from: Participant) =>
      case m: InitCommit =>
      case m: InitAbort =>
      case InitViewChange(from: Coordinator) =>
      case m: NewView =>
      case m: BaPrepare =>
      case m: BaCommit =>
      case m: BaPrePrepare =>
      case SendUnknownParticipants(participants: Set[Participant], from: Coordinator) =>
        this.participants |= participants
      case RequestUnknownParticipants(from: Coordinator) =>
        from ! Messages.SendUnknownParticipants(participants, context.self)
    }
    Behaviors.same
  }

}
