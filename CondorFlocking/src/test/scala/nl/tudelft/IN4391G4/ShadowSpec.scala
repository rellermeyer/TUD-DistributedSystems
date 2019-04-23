package nl.tudelft.IN4391G4

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import nl.tudelft.IN4391G4.machines.{JobContext, LambdaJob, WorkstationShadow}
import nl.tudelft.IN4391G4.messages.JobMessages.{AssignJobRequest, AssignJobResponse, AssignedJob, ExecuteJob, JobAssignment}
import nl.tudelft.IN4391G4.messages.MachineState.Available
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class ShadowSpec (_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {
  //#test-classes

  def this() = this(ActorSystem("ShadowSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "The Shadow Actor" should {
    "send a Job to a workstation" in {
      // The testprobe will act as an available workstation
      val testProbe = TestProbe()
      val shadow = system.actorOf(Props(new WorkstationShadow(null)))
      val id = UUID.randomUUID()
      val jobContext = JobContext(id, Available)
      val assignment = JobAssignment(jobContext, testProbe.ref)
      shadow ! AssignedJob(LambdaJob(id, () => "Hi"), assignment)
      testProbe.expectMsg(AssignJobRequest(jobContext))
    }
  }

  "The Shadow Actor" should {
    "send a Job to a starter when the assignment succeeded" in {
      // The testprobe will act as an available workstation
      val testProbe = TestProbe()
      val shadow = system.actorOf(Props(new WorkstationShadow(null)))
      val id = UUID.randomUUID()
      val jobContext = JobContext(id, Available)
      val assignment = JobAssignment(jobContext, testProbe.ref)
      val job = LambdaJob(id, () => "Hi")
      shadow ! AssignedJob(job, assignment)
      testProbe.expectMsg(AssignJobRequest(jobContext))
      testProbe.send(shadow, AssignJobResponse(jobRequestAccepted = true, Some(testProbe.ref)))
      testProbe.expectMsg(ExecuteJob(job))
    }
  }
}