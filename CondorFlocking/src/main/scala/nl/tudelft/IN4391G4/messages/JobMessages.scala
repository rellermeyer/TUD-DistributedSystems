package nl.tudelft.IN4391G4.messages

import akka.actor.ActorRef
import nl.tudelft.IN4391G4.machines.{Job, JobContext}

object JobMessages {

  trait JobMessage

  final case class AssignJobRequest(jobContext: JobContext) extends JobMessage
  final case class AssignJobResponse(jobRequestAccepted: Boolean, starterRef: Option[ActorRef]) extends JobMessage

  //extra message only for inserting job info into the system (basically a local message from Workstation to scheduler daemon
  final case class InsertJob(jobContext: JobContext, job: Job) extends JobMessage
  final case class SubmitJob(jobContext: JobContext) extends JobMessage
  final case class JobAssignment(jobContext: JobContext, machine: ActorRef) extends JobMessage
  final case class AssignedJob(job: Job, jobAssignment: JobAssignment) extends JobMessage
  final case class ExecuteJob(job: Job) extends JobMessage
  final case class JobResult(runtime: Long, exitCode: Int, outputStream: String, errorStream: String) extends JobMessage
  final case class JobAssignmentFailure(job: Job, jobContext: JobContext) extends JobMessage
  final case class JobFinished() extends JobMessage
}
