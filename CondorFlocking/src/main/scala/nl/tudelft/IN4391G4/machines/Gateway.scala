package nl.tudelft.IN4391G4.machines

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.{Iterator, UUID}

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.typesafe.config.{Config, ConfigValue}
import nl.tudelft.IN4391G4.extensions.ActorExtensions
import nl.tudelft.IN4391G4.messages.JobMessages.{AssignJobRequest, AssignJobResponse, JobAssignment, SubmitJob}
import nl.tudelft.IN4391G4.messages.StateMessages._
import nl.tudelft.IN4391G4.messages.MachineState

class Gateway(override val system: ActorSystem, override val machineConfig: Config) extends JobProcessingMachine(system = system, machineConfig = machineConfig) {

  val scheduler: ActorRef = system.actorOf(Props(new GatewaySchedulerDaemon(machineConfig)), name = machineName+"-scheduler")
  val starter: ActorRef = system.actorOf(Props(new GatewayStarterDaemon(machineConfig)), name = machineName+"-starter")

  var state: MachineState = MachineState.Busy

  override def start(): Unit = {
    super.start()
  }
}

// Daemons..
class GatewaySchedulerDaemon(machineConfig: Config) extends SchedulerDaemon {

  private var storedJobRequestSenders: Map[UUID, ActorRef] = Map()

  def receive = {
    case a: JobAssignment => onJobAssignment(a)
    case j: AssignJobRequest => onAssignJobRequest(j)
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  def onAssignJobRequest(j:AssignJobRequest) = {
    log.info("Send assignment from gateway for scheduling")
    storedJobRequestSenders = storedJobRequestSenders + (j.jobContext.jobId -> sender)
    val centralManager = ActorExtensions.getActorRefFromConfig(context.system, machineConfig, "_.manager")
    centralManager match {
      case Some(cmRef) => cmRef ! SubmitJob(j.jobContext)
      case None => println("Central manager not found on gateway assignjobrequest message")
    }
  }

  def onJobAssignment ={
    s: JobAssignment =>
      val shadow = createShadow(s)
      shadow ! s
  }

  override def createShadow(s: JobAssignment): ActorRef = {
    val gwRef = storedJobRequestSenders.get(s.jobContext.jobId)
    gwRef match {
      case Some(ref) => context.actorOf(Props(new GatewayShadow(machineConfig, ref)))
      case None => throw new NoSuchElementException("Could not find stored gateway in GatewaySchedulerDaemon")
    }

  }


}

class GatewayStarterDaemon(machineConfig: Config) extends StarterDaemon with StateDaemon {
  //Map of Workstation -> State
  private var storedStates: Map[ActorRef, MachineState] = Map()
  private val IntervalNotification = "interval"
  private var currentlyImpersonatingGateway: ActorRef = self

  val cancellableInterval = context.system.scheduler.schedule(10000 milliseconds, 1000 milliseconds, self, IntervalNotification)

  def receive = {
    case assignJobRequest:AssignJobRequest => onAssignJobRequest(assignJobRequest)
    case stateUpdates:StateUpdates => onStateUpdates(stateUpdates)
    case IntervalNotification => requestStates()
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  override def onAssignJobRequest(assignJobRequest: AssignJobRequest): Unit = {
    //After creating starter, send the assignJobRequest and the starter will handle the rest
    val starter = createStarter()
    starter ! assignJobRequest
  }

  override def createStarter(): ActorRef = {
    val submissionMachineShadow = sender()
    //val gatewayStarterDaemonChild =
    log.info("Starting GW Daemon Child for impersonated machine "+currentlyImpersonatingGateway)
    val starter: ActorRef = context.actorOf(Props(new GatewayStarterDaemonChild(submissionMachineShadow, currentlyImpersonatingGateway)))
    starter
  }

  def requestStates(): Unit = {
    val centralManager = ActorExtensions.getActorRefFromConfig(context.system, machineConfig, "_.manager")
    centralManager match {
      case Some(cmRef) =>
        log.info(s"Request state request")
        cmRef ! RequestStates()
      case None => log.error("Central manager not found on gateway states request")
    }
  }

  def onStateUpdates(stateUpdates :StateUpdates): Unit = {
    //Received states from other GW or from CM
    val centralManager = ActorExtensions.getActorRefFromConfig(context.system, machineConfig, "_.manager")
    centralManager match {
      case Some(cmRef) => {
        if(sender.equals(cmRef)) {
          broadcastStatesToGateways(stateUpdates)
        }
        else {
          //Received from GW, merge map
          storedStates = storedStates ++ stateUpdates.states
          sendState()
        }
      }
      case None => {
        println("Central manager not found on state updates")
      }
    }
  }

  def broadcastStatesToGateways(stateUpdates :StateUpdates): Unit = {
    val gatewayIterator: Iterator[ConfigValue] = machineConfig.getList("_.gateways").iterator()
    while(gatewayIterator.hasNext) {
      val gatewayConf: Config = gatewayIterator.next.atKey("_")
      val gateway = ActorExtensions.getActorRefFromConfig(context.system, gatewayConf, "_", (gwRef: ActorRef) => {
        log.info(s"Send to gateway ${gwRef}: ${stateUpdates.states}")
        gwRef ! StateUpdates(stateUpdates.states)
      })
    }
  }

  override def onRequestStates: RequestStates => Unit = {
    case _ => sender ! StateUpdates(storedStates)
  }

  override def sendState(): Unit = {
    //Check if there is an available state and send random if any (random is trivial in our case as machine state is binary)
    //If none available, send busy state
    val availableMachine = storedStates.filter(!_._1.toString().contains("gateway")).find(_._2 == MachineState.Available).getOrElse((None, None))._1
    log.info(s"Send random available state ${availableMachine}")
    val stateToSend = availableMachine match {
      case ref: ActorRef => {
        currentlyImpersonatingGateway = ref
        MachineState.Available
      }
      case None => MachineState.Busy
    }
    val centralManager = ActorExtensions.getActorRefFromConfig(context.system, machineConfig, "_.manager")
    centralManager match {
      case Some(cmRef) => cmRef ! StateUpdate(stateToSend)
      case None => println("Central manager not found on gateway states request")
    }
  }
}

object GatewayStarterDaemon {

}

object GatewayShadow{
  def props = Props[GatewayShadow]
}

class GatewayShadow(machineConfig: Config, submissionPoolGateway: ActorRef) extends Shadow {

  def receive = {
    case assignJobResponse: AssignJobResponse => onAssignJobResponse(assignJobResponse)
    case jobAssignment: JobAssignment => onJobAssignment(jobAssignment)
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  def onJobAssignment(assignment: JobAssignment): Unit = {
    assignment.machine ! AssignJobRequest(assignment.jobContext)
  }

  override def onAssignJobResponse(assignJobResponse: AssignJobResponse): Unit = {
    submissionPoolGateway ! assignJobResponse
  }

}

class GatewayStarterDaemonChild(submissionMachineShadow: ActorRef, executionPoolGateway: ActorRef) extends Actor with ActorLogging{

  def receive = {
    case assignJobRequest:AssignJobRequest=> onAssignJobRequest(assignJobRequest)
    case assignJobResponse:AssignJobResponse => onAssignJobResponse(assignJobResponse)
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  def onAssignJobRequest(assignJobRequest: AssignJobRequest) = {
    executionPoolGateway ! assignJobRequest
  }

  def onAssignJobResponse(assignJobResponse: AssignJobResponse) = {
    submissionMachineShadow ! assignJobResponse
  }
}