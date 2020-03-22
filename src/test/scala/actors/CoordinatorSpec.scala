package actors

import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, LoggingTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.Behaviors
import controllers.ActorStart.ActorStartMessage
import org.scalatest.wordspec.AnyWordSpecLike
import util.Messages
import util.Messages._

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A transaction" must {
    "succeed with 1 coordinator and 1 participant" in {
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      ps.foreach(p=>p!PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect{
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x=>testKit.stop(x))
      ps.foreach(x=>testKit.stop(x))
    }
  }
  "2 transactions" must {
    "succeed with 4 coordinators and 1 participant" in {
      val (cs, ps) = spawnAll(4, 1)
      val t0 = Transaction(0)
      ps.foreach(p=>p!PropagateTransaction(t0))
      val t1 = Transaction(1)
      ps.foreach(p=>p!PropagateTransaction(t1))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect{
        cs.foreach(c => c ! Messages.InitCommit(t0.id, p))
      }
      LoggingTestKit.info("Committed transaction 1").expect{
        cs.foreach(c => c ! Messages.InitCommit(t1.id, p))
      }
      cs.foreach(x=>testKit.stop(x))
      ps.foreach(x=>testKit.stop(x))
    }
  }

  def spawnAll(nCoordinators:Int, nParticipants: Int): (Array[Messages.Coordinator], Array[Messages.Participant]) ={
    val coordinators = new Array[Messages.Coordinator](nCoordinators)
    for(x <- 0 until nCoordinators){
      coordinators(x) = spawn(Coordinator(),"Coordinator-" + x)
    }
    coordinators.foreach { x => x ! Messages.Setup(coordinators) }
    val participants = new Array[Messages.Participant](nParticipants)
    for(x <- 0 until nParticipants){
      participants(x) = spawn(Participant(coordinators),"Participant-" + x)
    }
    (coordinators, participants)
  }
}
