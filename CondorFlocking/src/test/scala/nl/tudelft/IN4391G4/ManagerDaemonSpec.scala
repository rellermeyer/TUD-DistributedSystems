package nl.tudelft.IN4391G4

import java.util.UUID

import akka.actor.{ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import nl.tudelft.IN4391G4.machines.{JobContext, ManagerDaemon}
import nl.tudelft.IN4391G4.messages.JobMessages.{JobAssignment, SubmitJob}
import nl.tudelft.IN4391G4.messages.MachineState.{Available, Busy}
import nl.tudelft.IN4391G4.messages.StateMessages.{RequestStates, StateUpdate, StateUpdates}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class ManagerDaemonSpec(_system: ActorSystem)
  extends TestKit(_system)
    with Matchers
    with WordSpecLike
    with BeforeAndAfterAll {
  //#test-classes

  def this() = this(ActorSystem("ManagerDaemonSpec"))

  override def afterAll: Unit = {
    shutdown(system)
  }

  "The CentralManager Actor" should {
    "queue a job to the first available workstation" in {
      // The testprobe will act as an available workstation
      val testProbe = TestProbe()
      val manager = system.actorOf(Props(new ManagerDaemon(null)))
      testProbe.send(manager, StateUpdate(Available))
      val jobContext = JobContext(UUID.randomUUID(), Available)
      testProbe.send(manager, SubmitJob(jobContext))
      testProbe.expectMsg(JobAssignment(jobContext, testProbe.ref))
    }

    "queue a job until a machine becomes available" in {
      val testProbe = TestProbe()
      val manager = system.actorOf(Props(new ManagerDaemon(null)))
      val jobContext = JobContext(UUID.randomUUID(), Available)
      testProbe.send(manager, SubmitJob(jobContext))
      testProbe.send(manager, StateUpdate(Available))
      testProbe.expectMsg(JobAssignment(jobContext, testProbe.ref))
    }

    "not assign a job to a busy machine" in {
      val testProbe = TestProbe()
      val manager = system.actorOf(Props(new ManagerDaemon(null)))
      testProbe.send(manager, StateUpdate(Busy))
      val jobContext = JobContext(UUID.randomUUID(), Available)
      testProbe.send(manager, SubmitJob(jobContext))
      testProbe.expectNoMessage()
    }

    "store previously sent machine states" in {
      val testProbe = TestProbe()
      val manager = system.actorOf(Props(new ManagerDaemon(null)))
      testProbe.send(manager, StateUpdate(Available))
      val expectedState = Map((testProbe.ref, Available))
      testProbe.send(manager, RequestStates())
      testProbe.expectMsg(StateUpdates(expectedState))
    }
  }
}