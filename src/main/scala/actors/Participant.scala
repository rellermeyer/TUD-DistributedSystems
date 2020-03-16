package actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import util._
import Messages._

import scala.collection.mutable.ListBuffer

object Participant {
  var decisionLog: ListBuffer[Boolean] = ListBuffer()
  def apply(coordRef : ActorRef[CoordinatorMessage]): Behavior[Messages.ParticipantMessage] = {

    Behaviors.receive {(context, message) =>
    message match {
      case m: Greet =>
        context.log.info ("Hello {}", m.whom)
        m.replyTo ! Greeted (m.whom, context.self)

      case m : ParticipantStart =>   // This message is used to let the participant know when to start sending commit requests
        coordRef ! Messages.RegisterWithCoordinator(context.self)
        startWorkflow(m.partRef,coordRef)

      case Messages.Prepare (from: Coordinator) =>
        println ("Participant: Prepare message received from "+from)
        from ! Messages.Prepared(null,context.self)

      case m: Abort =>
        println("Participant: Abort received from Coordinator")
        decisionLog += m.BAResult
        if(canMakeDecision(true,decisionLog)) {
          m.from ! Messages.Aborted(null, context.self)
          println("Participant: Can make decision")
        } else
          println("Participant: Cannot make decision")

      case m: Commit =>
        println("Participant: Commit received from Coordinator")
        decisionLog += m.BAResult
        if(canMakeDecision(true,decisionLog)) {
          m.from ! Messages.Committed(null, context.self)
          println("Participant: Can make decision")
        } else
          println("Participant: Cannot make decision")

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
    coordRef ! Messages.InitCommit(null,partRef)

    /*while(true){
      Thread.sleep(1000)
      coordRef ! Messages.InitCommit(null,partRef)
    }*/
  }

  def canMakeDecision(decision: Boolean/*true=commit,false=abort*/, decisionLog: ListBuffer[Boolean]): Boolean ={
    var NumOfDecisions = 0

    println(decisionLog)

    decisionLog.foreach( x =>
      if(x == decision)
        NumOfDecisions = NumOfDecisions + 1
    )
    val f = 1 //Just for the example to work
    if(NumOfDecisions > f + 1) true else false
  }
}
