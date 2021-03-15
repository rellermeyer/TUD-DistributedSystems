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
    
    sys.ShutdownHookThread {
      println("Unregistering JobManager")
      registry.unbind(jobManagerName)
    }
  }
}

class JobManager extends UnicastRemoteObject with JobManagerInterface {
  var taskManagerIdCounter = 0
  val taskManagers = ArrayBuffer[TaskManagerInfo]()
  val reconfigurationManager = ReconfigurationManager
  var alpha = 0.8.toFloat

  // register the poor taskmanager
  def register(): Int = {
    taskManagers.append(
      new TaskManagerInfo(
        id = taskManagerIdCounter,
        0,
        0,
        Array.empty[Latency],
        Array.empty[BW],
        0,
        0
      )
    )
    taskManagerIdCounter = taskManagerIdCounter + 1
    return taskManagerIdCounter-1;
  }

  def unregister(id: Int): Unit = {
    taskManagers.remove(taskManagers.indexOf(id))
  }

  var jobIDCounter = 0
  def runStaticJob() = {
    val id1 = taskManagers(taskManagers.length - 1).id
    val id2 = taskManagers(taskManagers.length - 2).id
    val id3 = taskManagers(taskManagers.length - 3).id
    val id4 = taskManagers(taskManagers.length - 4).id

    val tm1 = Naming.lookup("taskmanager" + id1).asInstanceOf[TaskManagerInterface]
    val tm2 = Naming.lookup("taskmanager" + id2).asInstanceOf[TaskManagerInterface]
    val tm3 = Naming.lookup("taskmanager" + id3).asInstanceOf[TaskManagerInterface]
    val tm4 = Naming.lookup("taskmanager" + id4).asInstanceOf[TaskManagerInterface]

    tm1.assignTask(new Task(jobID = jobIDCounter, from = -1, to = id2, operator = "data"))
    tm2.assignTask(new Task(jobID = jobIDCounter, from = id1, to = id3, operator = "map"))
    tm3.assignTask(new Task(jobID = jobIDCounter, from = id2, to = id4, operator = "reduce"))
    tm4.assignTask(new Task(jobID = jobIDCounter, from = id3, to = -1, operator = "reduce"))

    jobIDCounter = jobIDCounter + 1
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