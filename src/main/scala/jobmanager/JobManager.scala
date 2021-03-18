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
  var taskManagerIdCounter = -1
  val taskManagers = ArrayBuffer[TaskManagerInfo]()
  val reconfigurationManager = ReconfigurationManager

  // register the poor taskmanager
  def register(): Int = {
    taskManagerIdCounter += 1
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
    taskIDCounters += 0 // initialize task counter to 0
    return taskManagerIdCounter;
  }

  var jobIDCounter = 0
  val taskIDCounters = ArrayBuffer.empty[Int] // for each TM a task counter
  
  def runJob(ops: Array[String], parallelisms: Array[Int]): Boolean = {
    val totalParallelism = parallelisms.sum
    // Call ILP solver with totalParallelism

    val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)

    if (ps == null) {
      println("Cannot create optimal execution plan.")
      return false
    }

    // Create execution plan
    val plan = ExecutionPlan.createPlan(taskManagers, ps, ops, parallelisms, taskIDCounters) 

    // Use plan to assign tasks to TMs
    for (row <- plan.indices) {
      plan(row).foreach { case (tm, taskID) => {
        val from = if (row == 0) {Array.empty[Int]} else {
          plan(row - 1).map(x => taskManagers(x._1).id).toArray
        }
        val to = if (row == plan.length - 1) {Array.empty[Int]} else {
          plan(row + 1).map(x => taskManagers(x._1).id).toArray
        }
        val toTaskIDs = if (row == plan.length - 1) {Array.empty[Int]} else {
          plan(row + 1).map(x => taskManagers(x._2).id).toArray
        }
        val tmi = Naming.lookup("taskmanager" + taskManagers(tm).id).asInstanceOf[TaskManagerInterface]
        tmi.assignTask(new Task(
          jobIDCounter,
          taskID,
          from,
          to,
          toTaskIDs,
          operator = if (row > 0) ops(row - 1) else "data"
        ))
      }}
    }
    jobIDCounter += 1 // increment job counter
    return true
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
    // counter = counter + 1
    println("Received report from " + id)
    taskManagers(id).numSlots = numSlots
    taskManagers(id).latenciesToSelf = latenciesToSelf
    taskManagers(id).bandwidthsToSelf = bandwidthsToSelf
    taskManagers(id).ipRate = ipRate
    taskManagers(id).opRate = opRate

    // TODO: only call it once in a while not all the time new data comes in
    // TODO: implement actual parallelism (need to increase or decrease, scale up or down)
    // if (counter == 3) {
    //   for (i <- taskManagers.indices) {
    //     println(taskManagers(i).bandwidthsToSelf.mkString(", "))
    //   }
    //   reconfigurationManager.solveILP(taskManagers, 1.0.toFloat, alpha)
    // }
  }
}

case class Latency(var fromID: Int, var time: Float)
case class BW(var fromID: Int, var rate: Float)
