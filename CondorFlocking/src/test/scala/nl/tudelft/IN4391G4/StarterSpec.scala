package nl.tudelft.IN4391G4

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import nl.tudelft.IN4391G4.machines.{LambdaJob, Starter}
import nl.tudelft.IN4391G4.messages.JobMessages.{ExecuteJob, JobResult}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class StarterSpec (_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {
  //#test-classes

  def this() = this(ActorSystem("StarterSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "The Starter Actor" should {
    "execute a Job and send the result" in {
      val testProbe = TestProbe()
      val starter = system.actorOf(Starter.props())
      testProbe.send(starter, ExecuteJob(LambdaJob(UUID.randomUUID(), () => "Hi")))
      testProbe.expectMsgPF() {
        // don't care about runtime
        case ok@JobResult(_, 0, "Hi", "") => ok
      }
    }
  }
}