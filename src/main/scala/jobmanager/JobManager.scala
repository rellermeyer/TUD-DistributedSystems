// package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import executionplan._
import scala.collection.mutable.ArrayBuffer
// import taskmanager._

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry(1098)
    val JobManager = new JobManager
    val jobManagerName = "jobmanager"

    registry.bind(jobManagerName, JobManager)
    println("JobManager bound!")
    
    sys.addShutdownHook {
      registry.unbind(jobManagerName)
    }
  }
}

class JobManager extends UnicastRemoteObject with JobManagerInterface {
  val taskManagers = ArrayBuffer[TaskManagerInfo]()
  val reconfigurationManager = ReconfigurationManager
  var alpha = 0.8.toFloat

  // register the poor taskmanager
  def register(): Int = {
    val tmsLength = taskManagers.length
    taskManagers.append(
      new TaskManagerInfo(
        tmsLength,
        0,
        Array.empty[Latency],
        Array.empty[BW],
        0,
        0
      )
    )
    return tmsLength;
  }

  // update metrics about a taskmanager
  def monitorReport(
      id: Int,
      numSlots: Int,
      latenciesToSelf: Array[Latency],
      bandwidthsToSelf: Array[BW],
      ipRate: Int,
      opRate: Int
  ) = {
    println ("Received report from " + id)
    taskManagers(id).numSlots = numSlots
    taskManagers(id).latenciesToSelf = latenciesToSelf
    taskManagers(id).bandwidthsToSelf = bandwidthsToSelf
    taskManagers(id).ipRate = ipRate
    taskManagers(id).opRate = opRate

    // TODO: only call it once in a while not all the time new data comes in
    // TODO: implement actual parallelism (need to increase or decrease, scale up or down)
    reconfigurationManager.solveILP(taskManagers, 1.0.toFloat, alpha)
  }
}


case class Latency(var fromID: Int, var time: Float)
case class BW(var fromID: Int, var rate: Float)
case class TaskManagerInfo(
    id: Int,
    var numSlots: Int,
    var latenciesToSelf: Array[Latency],
    var bandwidthsToSelf: Array[BW],
    var ipRate: Int,
    var opRate: Int
)
