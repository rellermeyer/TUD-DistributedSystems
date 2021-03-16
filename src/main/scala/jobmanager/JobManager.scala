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
    // val ps = Array(1, 2, 2, 2)
    // val ops = Array("map", "reduce")
    // val parallelisms = Array(5, 1)

    // val dataSource = taskManagers(0).id

    // for (taskID <- ops.indices) {
    //   var parallelism = parallelisms(taskID)
    //   while (parallelism > 0) {
    //     for (s <- ps.indices) {
    //       var pss = ps(s)
    //       while (pss > 0) {
    //         // assign taskID to taskManager s

    //         pss += 1
    //         parallelism -= 1
    //       }
    //     }
    //   }
    // }

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
        from = Array(id2, id2, id3),
        to = Array(),
        toTaskIDs = Array(),
        operator = "reduce"
      )
    )

    jobIDCounter += 1
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
