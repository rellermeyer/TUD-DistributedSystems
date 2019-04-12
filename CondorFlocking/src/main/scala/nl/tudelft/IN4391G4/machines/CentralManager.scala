package nl.tudelft.IN4391G4.machines

import akka.actor.{ActorLogging, ActorRef, ActorSystem, Props}
import com.typesafe.config.Config
import nl.tudelft.IN4391G4.extensions.{ConfigExtensions, HardwareExtensions}
import nl.tudelft.IN4391G4.messages.JobMessages.{JobAssignment, SubmitJob}
import nl.tudelft.IN4391G4.messages.MachineState
import nl.tudelft.IN4391G4.messages.MachineState.Available
import nl.tudelft.IN4391G4.messages.StateMessages.{RequestStates, StateUpdate, StateUpdates}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.collection.immutable.Queue
import scala.util.Random

class CentralManager(override val system : ActorSystem, override val machineConfig: Config) extends StateProcessingMachine(system = system, machineConfig = machineConfig) {

  val manager: ActorRef = system.actorOf(Props(new ManagerDaemon(machineConfig)), name = machineName) // name daemon as machine, the machine provides nothing further anyway

}

case class QueueItem(context: JobContext, sender: ActorRef)

class ManagerDaemon(machineConfig: Config) extends StateDaemon with ActorLogging {
  private val managerName = ConfigExtensions.getStringSafe(machineConfig, "_.name", "workstation_unknown")
  private val IntervalNotification = "CpuLoadInterval"
  private var storedState: Map[ActorRef, MachineState] = Map()
  private var queue: Queue[QueueItem] = Queue()

  val cancellableInterval = context.system.scheduler.schedule(2500 milliseconds, 1000 milliseconds, self, IntervalNotification)

  def receive = {
    case stateUpdate: StateUpdate => onStateUpdate(stateUpdate)
    case requestStates: RequestStates => onRequestStates(requestStates)
    case submitJob: SubmitJob => onSubmitJob(submitJob)
    case IntervalNotification => onCpuLoadInterval
    case unknown => log.error(s"Received unknown message ${unknown}")
  }

  def onCpuLoadInterval(): Unit ={
    log.info(s"${System.currentTimeMillis()} Manager ${managerName} CPU Load ${HardwareExtensions.getCpuLoad()}")
  }

  def onStateUpdate: StateUpdate => Unit = {
    case StateUpdate(state) =>
      if(state == Available && queue.nonEmpty){
        queue.dequeue match {
          case (QueueItem(j, s), nq) =>
            log.info(s"Assign queued job from ${s.path} to ${sender.path}")
            // s == submitter, sender == workstation
            s ! JobAssignment(j, sender)
            queue = nq
        }
      } else{
        storedState = storedState + (sender -> state)
      }
  }

  override def onRequestStates = {
    case RequestStates() => sender ! StateUpdates(storedState)
  }
  
  def onSubmitJob: SubmitJob => Unit = {
    case SubmitJob(j: JobContext) =>
      log.info(s"Got SubmitJob ${j.jobId}")
      j match {
      case JobContext(_, requiredState) =>
        val matches = storedState.filter { case (_, v) => requiredState == v} // search states matching the requirements
        Random.shuffle(matches.keys).headOption match {
          case Some(ref) =>
            log.info(s"Assign job from ${sender.path} to ${ref.path}")
            // sender == submitter
            sender ! JobAssignment(j, ref)
            storedState = storedState + (ref -> MachineState.Busy)
            log.info(s"State is now ${storedState}")
          case None =>
            queue = queue.+:(QueueItem(j, sender))
        }
    }
  }
}

object ManagerDaemon{
  def props = Props[ManagerDaemon]
}