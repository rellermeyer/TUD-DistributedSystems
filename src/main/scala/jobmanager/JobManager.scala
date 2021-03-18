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
  val alpha = 0.8.toFloat // please move this to ReconfigurationManager

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

  /**
     * 1
     *   \
     *    4
     *  /   \
     * 2      6
     *   \  /
     *    5
     *   /
     * 3
    */

    // 1 to = [4, 5] from = [dataSource]
    // 2 to = [4, 5] from = [dataSource]
    // 3 to = [4, 5] from = [dataSource]
    // 4 to = [6]    from = [1, 2, 3]
    // 5 to = [6]    from = [1, 2, 3]
    // 6 to = []     from = [4, 5]

    /**
     * plan = 
     * [
     *  data: [tm1_0]
     *  op0:  [tm2_0, tm3_0, tm3_1]
     *  op1:  [tm3_2, tm4_0]
     *  op2:  [tm4_1]
     * ]
     **/
  var jobIDCounter = 0
  val taskIDCounters = ArrayBuffer.empty[Int] // for each TM a task counter
  
  def runStaticJob() = {
    // (map, 3), (map, 2), (reduce, 1)
    val ops = Array("map", "map", "reduce")
    val parallelisms = Array(3, 2, 1)

    val dataSources = Array(0) // indices of taskManagers who will provide data

    val totalParallelism = parallelisms.sum
    // Call ILP solver with totalParallelism
    val ps = Array(1, 3, 2) // number of tasks do deploy per site (from the ILP)

    // Plan holds for each operator the tuple (TM, TaskID)
    // length of ops + 1 row for data sources
    val plan = Array.fill(ops.length + 1)(ArrayBuffer.empty[(Int, Int)])

    // Add data sources to plan
    dataSources.foreach(x => {
      plan(0) += ((x, taskIDCounters(x))) // add to plan
      taskIDCounters(x) += 1 // increment task counter for this TM
    })

    // Add operators to plan
    for (op <- ops.indices) {            // for each operator
      for (tm <- taskManagers.indices) { // for each task manager

        // While current operator still needs tasks assigned
        //  AND current task manager still has available slots
        while (parallelisms(op) > 0 && ps(tm) > 0) {
          plan(op + 1) += ((tm, taskIDCounters(tm))) // add to plan
          taskIDCounters(tm) += 1      // increment task counter for current TM
          ps(tm) -= 1                  // decrement number of available slots for current TM
          parallelisms(op) -= 1        // decrement number of times current operator still needs to be assigned
        }
      }
    }
    // Print plan
    for (i <- plan.indices) {
      println (plan(i).mkString(", "))
    }

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
    counter = counter + 1
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
