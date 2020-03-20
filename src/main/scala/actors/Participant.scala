package actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import util.Messages.CommitOrAbort.CommitOrAbort
import util.Messages._
import util._

import scala.collection.mutable.ListBuffer

object Participant {
  val f = 1 //Just for the example to work // TODO: fix this
  var decisionLog: ListBuffer[CommitOrAbort] = ListBuffer()

  def apply(coordRef: ActorRef[CoordinatorMessage]): Behavior[Messages.ParticipantMessage] = {

    Behaviors.receive { (context, message) =>
      message match {

        case ParticipantStart() => // This message is used to let the participant know when to start sending commit requests
          coordRef ! Messages.RegisterWithCoordinator(context.self)

          // Infinite loop which sends commitRequests every second
          for (x <- 1 to 1) {
            val t = Transaction(x)
            coordRef ! Messages.InitCommit(t, context.self)
            Thread.sleep(1000)
          }

        case Messages.Prepare(t: Transaction, from: Coordinator) =>
          context.log.info("Prepare message received from " + from)
          from ! Messages.Prepared(t, context.self)

        case m: Commit =>
          if (m.BAResult == CommitOrAbort.COMMIT) {
            context.log.info("Commit received from Coordinator")
          }
          else {
            context.log.info("Abort received from Coordinator")
          }
          decisionLog += m.BAResult
          if (canMakeDecision(m.BAResult)) {
            m.from ! Messages.CommitOutcome(null, m.BAResult, context.self)
            m.BAResult match {
              case util.Messages.CommitOrAbort.COMMIT =>
                context.log.info("Committed")
              case util.Messages.CommitOrAbort.ABORT =>
                context.log.info("Aborted")
            }
          }
          else
            context.log.info("Cannot make decision yet")
        /*
              // TODO
              case m: InitCommit => //only needed for multiple participants
         */
        case message: Messages.InitiatorMessage =>
          message match {
            case Messages.RegisterWithInitiator(from: Participant) =>
          }
      }
      Behaviors.same
    }
  }

  def canMakeDecision(decision: CommitOrAbort): Boolean = {
    decisionLog.count(x => x == decision) > f + 1
  }
}
