package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import util._
import Messages._

object Coordinator {
  var coordinators: Set[Coordinator] = null
  var participants: Set[Participant] = Set()
  def apply(): Behavior[CoordinatorMessage] = Behaviors.receive { (context, message) =>
    message match {
      case m: SendCoordinatorSet =>
        coordinators = m.coordinatorSet

      case m: Greet =>
        context.log.info("Hello {}", m.whom)
        m.replyTo ! Greeted(m.whom, context.self)

      case RegisterWithCoordinator(from: Participant) =>
        participants += from

      case m: Prepared =>
        println("Coordinator: Prepared message received from participant: "+m.from+". Transaction: "+m.t)

      case Aborted(t: Transaction, from: Participant) =>

      case m: InitCommit => //After receiving initCommit message, coordinator answers with a prepare message
        println("Coordinator: InitCommit received from "+m.from+". Transaction: "+m.t)
        participants.foreach(participant => participant ! Messages.Prepare(context.self))
        //Start byzantine agreement
        byzantineAgreement(coordinators,context.self) //Set of coordinator replicas answers

      case m: Committed =>
        println("Coordinator: Committed received from participant: "+m.from+".Transaction: "+m.t)

      case m: InitAbort =>
        println("Coordinator: InitAbort received from "+m.from+". Transaction: "+m.t)
        participants.foreach(participant => participant ! Messages.Prepare(context.self))
        //Start byzantine agreement
        byzantineAgreement(coordinators, context.self)

      case InitViewChange(from: Coordinator) =>
      case m: NewView =>
      case m: BaPrepare =>
      case m: BaCommit =>
      case m: BaPrePrepare =>
        // Here we must check if the output is commit or abort.
        println("Coordinator: Received BaPrePrepare")
        // We are simulating the result of the BAAlgorithm. Commit or abort message must be sent depending on the BAPrePrepare message Output parameter.
        val r = scala.util.Random
        if(r.nextInt % 2 == 0)
          participants.foreach( part => part ! Messages.Commit(context.self, true))
        else
          participants.foreach( part => part ! Messages.Commit(context.self, false))

      case SendUnknownParticipants(participants: Set[Participant], from: Coordinator) =>
        this.participants |= participants

      case RequestUnknownParticipants(from: Coordinator) =>
        from ! Messages.SendUnknownParticipants(participants, context.self)

    }
    Behaviors.same
  }

//TODO: execute byzantine agreement protocol and return results
  def byzantineAgreement(coordinators: Set[Coordinator], self: ActorRef[CoordinatorMessage]): Unit ={
    // Start BAAlgorithm
    coordinators.foreach( coord => coord ! Messages.BaPrePrepare(null,null,self))
  }
}
