package nl.tudelft.IN4391G4.machines

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import com.typesafe.config.Config
import nl.tudelft.IN4391G4.extensions.ConfigExtensions
import nl.tudelft.IN4391G4.messages.JobMessages._
import nl.tudelft.IN4391G4.messages.MachineState
import nl.tudelft.IN4391G4.messages.StateMessages.RequestStates

abstract class Machine(val system: ActorSystem, val machineConfig: Config) {

  private val machinename = ConfigExtensions.getString(machineConfig, "_.name")
  private val poolname = system.name

  if(poolname == "" || machinename == "") {
    throw new IllegalArgumentException(s"Invalid machine configuration, pool or name property is not specified. Got: _.pool = '$poolname' and _.name = '$machinename'")
  }

  val machineName: String = machinename

  def start(): Unit = {
    println(s"Machine '$machineName' in pool '${system.name}' has been started")
  }
}

abstract class JobProcessingMachine(override val system: ActorSystem, override val machineConfig: Config) extends Machine(system = system, machineConfig = machineConfig) {
  protected var state: MachineState
  def currentState: MachineState = state
  val scheduler: ActorRef
  val starter: ActorRef
}

abstract class StateProcessingMachine(override val system: ActorSystem, override val machineConfig: Config) extends Machine(system = system, machineConfig = machineConfig) {
  val manager: ActorRef
}

// Daemons..
// NOTE: changed to traits as GatewaySchedulerDaemon needs to extend two of these and scala only allows extending a single abstract class (but multiple traits)

trait SchedulerDaemon extends Actor with ActorLogging {
  def onJobAssignment: JobAssignment => Unit
  def createShadow(s: JobAssignment): ActorRef
}

trait StarterDaemon extends Actor with ActorLogging {
  def onAssignJobRequest(assignJobRequest: AssignJobRequest): Unit
  def createStarter(): ActorRef
  def sendState(): Unit
}

trait StateDaemon extends Actor with ActorLogging {
  def onRequestStates: RequestStates => Unit
}

trait Shadow extends Actor with ActorLogging {
  def onAssignJobResponse(assignJobResponse: AssignJobResponse): Unit
}