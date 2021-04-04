package nl.tudelft.IN4391G4.machines


import java.util.UUID

import nl.tudelft.IN4391G4.gui.{UILauncher, WorkstationUI}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import nl.tudelft.IN4391G4.extensions.{ActorExtensions, ConfigExtensions}
import nl.tudelft.IN4391G4.messages.JobMessages._
import nl.tudelft.IN4391G4.messages.StateMessages.StateUpdate
import nl.tudelft.IN4391G4.messages.{MachineState, MachineStateOrdering}
import rx.subjects.PublishSubject

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class Workstation(override val system: ActorSystem,override val machineConfig: Config) extends JobProcessingMachine(system = system, machineConfig = machineConfig) {

  val scheduler: ActorRef = system.actorOf(Props(new WorkstationSchedulerDaemon(machineConfig)), name = machineName+"-scheduler")

  val starter: ActorRef = system.actorOf(Props(new WorkstationStarterDaemon(machineConfig)), name = machineName+"-starter")



  var state: MachineState = if(ConfigExtensions.getBooleanSafe(machineConfig, "_.submitOnly", false)) MachineState.Busy else MachineState.Available



  var uiRef: WorkstationUI = _
  var submitSubscription: rx.Subscription = _

  override def start(): Unit = {

    if(machineConfig.getBoolean("_.launchUI")) {
      launchUI()
    }

    WorkstationStarterDaemon.onStateChange.subscribe(data => {
      val (name: String, state: MachineState) = data
      if(name == machineName) {
        setState(state)
      }
    })

    WorkstationShadow.onResult.subscribe(data => {
      val (name: String, result: JobResult) = data
      if(name == machineName) {
        uiRef.newJobResult(result)
      }
    })

    super.start()
  }

  private def launchUI(): Unit = {
    uiRef = UILauncher.launchWorkstationUI(machineName, state)
    submitSubscription = uiRef.onSubmit.subscribe(data => {
      val (context, job) = data
      insertJob(context, job)
    })
  }

  private def insertJob(jobContext: JobContext, job: Job): Unit = {
    scheduler ! InsertJob(jobContext, job)
  }


  def setState(s: MachineState): Unit = {
    state = s
    if(uiRef != null) {
      uiRef.setState(s) // pass on new state to UI
    }
  }
}

class WorkstationSchedulerDaemon(machineConfig: Config) extends SchedulerDaemon {

  var submissionMap: Map[UUID, Job] = Map()

  def receive = {
    case j: JobAssignment => onJobAssignment(j)
    case i: InsertJob => onInsertJob(i.jobContext, i.job)
    case f: JobAssignmentFailure => onJobAssignmentFailure(f)
    case unknown => log.error(s"Received unknown message ${unknown}")
  }


  def sendSubmitJob(s: SubmitJob) {
    getAssignedCentralManager match {
      case Some(cmRef) => cmRef ! s
      case None => System.err.println("Error on job submit in WorkstationSchedulerDaemon")
    }
  }

  def onJobAssignmentFailure(failedJob: JobAssignmentFailure): Unit = {
    //When job assignment unsuccessful, requeue
    onInsertJob(failedJob.jobContext, failedJob.job)
  }

  def onInsertJob(jobContext: JobContext, job: Job) = {
    log.info(s"Received ${jobContext.jobId} ${jobContext.requiredState} ${job.toString()}")
    submissionMap = submissionMap + (jobContext.jobId -> job)
    println(s"new job submitted to ${context.self.path}, $jobContext, $job")
    sendSubmitJob(SubmitJob(jobContext))
  }

  override def onJobAssignment = {

    sendAssignment: JobAssignment =>
      val shadow = createShadow(sendAssignment)
      val queueEntry = submissionMap.find {
        case (id: UUID, job: Job) => id == sendAssignment.jobContext.jobId
      }.orNull;// search states matching the requirements

      if(queueEntry != null) {
        val job = queueEntry._2
        shadow ! AssignedJob(job, sendAssignment)

        // Job is assigned to shadow, remove it from our queue
        submissionMap = submissionMap - queueEntry._1
        // NOTE: this is not safe, if the shadow fails we lose the job.
        // Time constrains on project disallow us to implement the safe way.
        // The safe way: let shadow send message back to its parent (this class) on job completion
      } else {
        log.error("queueEntry not found")
      }
  }

  override def createShadow(s: JobAssignment): ActorRef = {
    context.actorOf(Props(new WorkstationShadow(machineConfig)))
  }

  private def getAssignedCentralManager : Option[ActorRef] = {
    ActorExtensions.getActorRefFromConfig(context.system, machineConfig, "_.manager")
  }
}

object WorkstationStarterDaemon {
  var states: Map[String, MachineState] = Map()

  private val statePublisher: PublishSubject[(String, MachineState)] = PublishSubject.create[(String, MachineState)]
  val onStateChange: rx.Observable[(String, MachineState)] = statePublisher.asObservable()

  def setState(name: String, s: MachineState): Unit = {
    states = states + (name -> s)
    this.statePublisher.onNext(name -> s)
  }
}

class WorkstationStarterDaemon(machineConfig: Config) extends StarterDaemon {
  val SendStateNotification = "SendStateNotification"
  val cancellableInterval = context.system.scheduler.schedule(0 milliseconds, 1000 milliseconds, self, SendStateNotification)


  var state: MachineState = if(ConfigExtensions.getBooleanSafe(machineConfig, "_.submitOnly", false)) MachineState.Busy else MachineState.Available

  private val workstationName = ConfigExtensions.getStringSafe(machineConfig, "_.name", "workstation_unknown")
  // variables for tracking activity
  private var available = 0l
  private var busy = 0l
  private var last = System.currentTimeMillis()
  private var lastTrackedActivity: MachineState = MachineState.Available

  def updateActivity(oldState: MachineState, newState: MachineState): Unit ={
    val elapsed = System.currentTimeMillis() - last
    if(oldState == MachineState.Available){
      available += elapsed
    } else if(oldState == MachineState.Busy){
      busy += elapsed
    } else{
      log.error("Unknown state")
    }
    last = System.currentTimeMillis()
    lastTrackedActivity = newState
    log.info(s"${System.currentTimeMillis()} Workstation ${workstationName}: Busy for ${busy} ms, Available for ${available} ms")
  }

  def setState(newState: MachineState): Unit ={
    if(state != newState){
      updateActivity(state, newState)
      WorkstationStarterDaemon.setState(machineConfig.getString("_.name"), newState)
      state = newState

    }
  }



  def receive = {
    case assignJobRequest:AssignJobRequest => onAssignJobRequest(assignJobRequest)
    case _: JobFinished =>
      setState(MachineState.Available)
      sendState()
    case SendStateNotification => sendState()
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  override def onAssignJobRequest(assignJobRequest: AssignJobRequest): Unit = {
    log.info(s"onAssignJobRequest, currently ${state}")
    val couldStartJob = MachineStateOrdering.lteq(assignJobRequest.jobContext.requiredState, state)
    val starter =
      if (couldStartJob) {
        Some(createStarter())
      } else {
        None
      }
    starter match {
      case Some(_) => setState(MachineState.Busy)
      case _ =>
    }
    sendAssignJobResponse(AssignJobResponse(couldStartJob, starter))
  }

  override def createStarter(): ActorRef = {
    context.actorOf(Starter.props())
  }

  def sendAssignJobResponse(assignJobResponse: AssignJobResponse): Unit = {
    log.info(s"Send assignment response ${assignJobResponse.jobRequestAccepted}, currently ${state}")
    sender() ! assignJobResponse
  }

  override def sendState(): Unit = {
    ActorExtensions.getActorRefFromConfig(context.system, machineConfig, "_.manager") match{
      case Some(cm) =>
        cm ! StateUpdate(state)
      case None => System.err.println("Can't send state to Central Manager")
    }
    updateActivity(state, state)
  }
}

object WorkstationSchedulerDaemon{
  def props(machineConfig: Config) = Props(new WorkstationSchedulerDaemon(machineConfig = machineConfig))
}

object WorkstationShadow {

  private val resultPublisher: PublishSubject[(String,JobResult)] = PublishSubject.create[(String,JobResult)]
  val onResult: rx.Observable[(String,JobResult)] = resultPublisher.asObservable()

  def setResult(name: String, result: JobResult): Unit = {
    this.resultPublisher.onNext(name -> result)
  }
}

class WorkstationShadow(machineConfig: Config) extends Shadow {
  private val submitterName = ConfigExtensions.getStringSafe(machineConfig, "_.name", "workstation_unknown")
  var jobToExecute: Job = _
  var jobContext: JobContext = _
  val started = System.currentTimeMillis()

  def receive = {
    case assignedJob:AssignedJob => onAssignedJob(assignedJob)
    case assignJobResponse: AssignJobResponse => onAssignJobResponse(assignJobResponse)
    case jobResult: JobResult => onJobResult(jobResult)
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  def onAssignedJob(assignedJob: AssignedJob): Unit = {
    assignedJob.jobAssignment.machine ! AssignJobRequest(assignedJob.jobAssignment.jobContext)
    jobToExecute = assignedJob.job
    jobContext = assignedJob.jobAssignment.jobContext
  }

  def onJobResult(jobResult: JobResult): Unit = {
    log.info(s"${System.currentTimeMillis()} Submitter ${submitterName}: Got result for ${jobContext.jobId} in ${jobResult.runtime} ms (${System.currentTimeMillis() - started} ms total): ${jobResult.outputStream}")
    WorkstationShadow.setResult(machineConfig.getString("_.name"), jobResult)
  }

  override def onAssignJobResponse(assignJobResponse: AssignJobResponse): Unit = {
    if(assignJobResponse.jobRequestAccepted && assignJobResponse.starterRef.isDefined) {
      sendJob(assignJobResponse.starterRef.get)
    }
    else {
      log.warning(s"Job assignment failed: ${jobToExecute.id}")
      context.parent ! JobAssignmentFailure(jobToExecute, jobContext)
    }
  }

  def sendJob(executionMachine: ActorRef): Unit = {
    executionMachine ! ExecuteJob(jobToExecute)
  }
}

class Starter extends Actor with ActorLogging{
  def receive = {
    case ExecuteJob(job: Job) => onExecuteJob(job)
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  def onExecuteJob(job: Job) = {
    sendJobResult(job.execute)
  }

  def sendJobResult(result: JobResult) = {
    // sender() will refer to the submission shadow as that will send the executable
    sender() ! result
    context.parent ! JobFinished()
    context.stop(self)
  }
}

object Starter {
  def props() = Props[Starter]
}