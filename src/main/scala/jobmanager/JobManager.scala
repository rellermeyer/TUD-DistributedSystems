// package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import executionplan._
import scala.collection.mutable.ArrayBuffer
// import taskmanager._

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry(1099)
    val JobManager = new JobManager
    registry.bind("jobmanager", JobManager)
    println("JobManager bound!")
  }
}

class JobManager extends UnicastRemoteObject with JobManagerInterface {
  val taskManagers = ArrayBuffer[TaskManagerInfo]()

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
    taskManagers(id).numSlots = numSlots
    taskManagers(id).latenciesToSelf = latenciesToSelf.clone()
    taskManagers(id).bandwidthsToSelf = bandwidthsToSelf.clone()
    taskManagers(id).ipRate = ipRate
    taskManagers(id).opRate = opRate
  }
}

case class Latency(fromID: Int, time: Float)
case class BW(fromID: Int, rate: Float)
case class TaskManagerInfo(
    id: Int,
    var numSlots: Int,
    var latenciesToSelf: Array[Latency],
    var bandwidthsToSelf: Array[BW],
    var ipRate: Int,
    var opRate: Int
)
