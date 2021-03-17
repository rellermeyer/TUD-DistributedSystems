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
  var taskManagerIdCounter = 0
  val taskManagers = ArrayBuffer[TaskManagerInfo]()
  val reconfigurationManager = ReconfigurationManager
  var alpha = 0.8.toFloat
  var counter: Int = 0

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
    return taskManagerIdCounter - 1;
  }

  def unregister(id: Int): Unit = {
    taskManagers.remove(taskManagers.indexOf(id))
  }

  var jobIDCounter = 0
  def runStaticJob() = {
    // (map, 3), (reduce, 1)
    val ops = Array("map", "map", "reduce")
    val parallelisms = Array(3, 2, 1)

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

    val totalParallelism = parallelisms.sum
    // Call ILP solver with totalParallelism
    val ps = Array(1, 3, 2) // number of tasks do deploy per site (from the ILP)

    val dataSource = taskManagers(0).id

    /**
     * Find the from and to arrays for each taskmanager
     * before assigning the tasks to them
    **/

    /**
     *  tm1 is data
     * [
     *  op0: [tm2_0, tm3_0, tm3_1]
     *  op1: [tm3_2, tm4_0]
     *  op2: [tm4_1]
     * ]
     * 
     * [
     *  0: [tm2_0 from=[data], tm3_0 from=[data], tm3_1 from=[data]]
     *  1: [tm3_2 from=[op0], tm4_0, from=[op0]]
     *  2: [tm4_1 from=[op1]]
     * ]
     * 
     **/


    // from: ArrayBuffer.empty[ArrayBuffer[Int]] // from[taskManagerId] = list of taskManagerId incoming connections
    // to: ArrayBuffer.empty[ArrayBuffer[Int]]   // to[taskManagerId] = list of taskManagerId outgoing connections
    // currFrom: ArrayBuffer.empty[Int]  // list of nodes connected to current node

    val plan = Array.fill(ops.length)(ArrayBuffer.empty[(Int, Int)])

    val taskIDCounters = Array.fill(taskManagers.length)(0)
    for (op <- ops.indices) {
      var par = parallelisms(op)
      for (tm <- taskManagers.indices) {
        while (par > 0 && ps(tm) > 0) {
          plan(op) += ((tm, taskIDCounters(tm)))
          taskIDCounters(tm) += 1
          ps(tm) -= 1
          par -= 1
        }
      }
    }

    for (i <- plan.indices) {
      println (plan(i).mkString(", "))
    }

    // for (taskID <- ops.indices) { // loop through the operators in this query
    //   var parallelism = parallelisms(taskID) // get parallelism for this operator
    //   while (parallelism > 0) { // keep assigning this operator to sites
    //     for (s <- ps.indices) {
    //       var pss = ps(s)
    //       while (pss > 0) {
    //         // assign taskID to taskManager s
    //         assignTask(jobIDCounter, taskID, from[taskID], to[taskID], /*TODO*/, )
    //         pss += 1
    //         parallelism -= 1
    //       }
    //     }
    //   }
    // }

    /*
    // initialize taskID counter for each TM
    val taskIDCounters = Array(taskManagers.length)(0)

    val id1 = taskManagers(0).id
    val id2 = taskManagers(1).id
    val id3 = taskManagers(2).id
    val id4 = taskManagers(3).id

    val tm1 = Naming.lookup("taskmanager" + id1).asInstanceOf[TaskManagerInterface]
    val tm2 = Naming.lookup("taskmanager" + id2).asInstanceOf[TaskManagerInterface]
    val tm3 = Naming.lookup("taskmanager" + id3).asInstanceOf[TaskManagerInterface]
    val tm4 = Naming.lookup("taskmanager" + id4).asInstanceOf[TaskManagerInterface]

    tm1.assignTask(
      new Task(
        jobIDCounter,
        taskID = 0,
        from = Array(),
        to = Array(id2, id3, id3),
        toTaskIDs = Array(0, 0, 1), // two separate tasks on 
        operator = "data"
      )
    )
    tm2.assignTask(
      new Task(
        jobIDCounter,
        taskID = 0,
        from = Array(id1),
        to = Array(id4),
        toTaskIDs = Array(0),
        operator = "map"
      )
    )
    tm3.assignTask(
      new Task(
        jobIDCounter,
        taskID = 0,
        from = Array(id1),
        to = Array(id4),
        toTaskIDs = Array(0),
        operator = "map"
      )
    )
    tm3.assignTask(
      new Task(
        jobIDCounter,
        taskID = 1,
        from = Array(id1),
        to = Array(id4),
        toTaskIDs = Array(0),
        operator = "map"
      )
    )
    tm4.assignTask(
      new Task(
        jobIDCounter,
        taskID = 0,
        from = Array(id2, id3, id3),
        to = Array(),
        toTaskIDs = Array(),
        operator = "reduce"
      )
    )

    jobIDCounter += 1
    */
  }


  def assignTask(jobID: Int, taskID: Int, from: Array[Int], 
                  to: Array[Int], toTaskIDs: Array[Int], operator: String) {
      new Task(
        jobID,
        taskID,
        from,
        to,
        toTaskIDs,
        operator
      )
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
