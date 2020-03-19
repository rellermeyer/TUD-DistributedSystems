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

      case ParticipantStart() =>   // This message is used to let the participant know when to start sending commit requests
        coordRef ! Messages.RegisterWithCoordinator(context.self)
        startWorkflow(context.self,coordRef)

      case Messages.Prepare (from: Coordinator) =>
        context.log.info("Prepare message received from "+from)
        from ! Messages.Prepared(null,context.self)

      case m: Commit =>
        if(m.BAResult) {
          context.log.info("Commit received from Coordinator")
        }
        else {
          context.log.info("Abort received from Coordinator")
        }
        decisionLog += m.BAResult
        if(canMakeDecision(m.BAResult,decisionLog)) {
          m.from ! Messages.CommitOutcome(null, m.BAResult/*true=commit,false=abort*/, context.self)
          if(m.BAResult) {
            context.log.info("Committed")
          }
          else {
            context.log.info("Aborted")
          }
        }
        else
          context.log.info("Cannot make decision yet")
/*
      case m: InitCommit => //only needed for multiple participants
 */
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
    coordRef ! Messages.InitCommit(null,true,partRef)

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
