// package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.io.FileReader
import java.util.Map
import executionplan._
import scala.collection.mutable.ArrayBuffer
// import taskmanager._

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    /*
     *  Create global registry for the JobManager and TaskManagers to connect to.
     */
    val registryPort = 1099
    val registry = LocateRegistry.createRegistry(registryPort)
    println("Registry running on port " + registryPort)

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
  val taskMgrsCount = 4

  var jobIDCounter = 0
  val taskIDCounters = ArrayBuffer.empty[Int] // for each TM a task counter

  val jobs = ArrayBuffer.empty[Job]
  new Thread {
    override def run {
      val jsonParser = new JSONParser()
      var taskMgrCfgs = ArrayBuffer[ArrayBuffer[TaskManagerInfo]]()
      try {
        val reader = new FileReader("config.json")
        //Read JSON file
        val obj = jsonParser.parse(reader);

        val configs = obj.asInstanceOf[JSONArray];
        val it = configs.iterator()

        //Iterate over configs array
        while (it.hasNext()) {
          val config = it.next().asInstanceOf[JSONObject]
          val taskMgrCfg = ArrayBuffer[TaskManagerInfo]()
          for (i <- 0 until taskMgrsCount) {
            val json_cfg = config.get(i.toString).asInstanceOf[JSONObject]
            var latencies = new Array[Latency](0)
            var bws = new Array[BW](0)
            val latencies_set =
              json_cfg.get("latencies").asInstanceOf[JSONObject].entrySet()
            val bws_set =
              json_cfg.get("bandwidth").asInstanceOf[JSONObject].entrySet()
            val json_latencies =
              json_cfg.get("latencies").asInstanceOf[JSONObject]
            val json_bws = json_cfg.get("bandwidth").asInstanceOf[JSONObject]
            val latency_it = latencies_set.iterator()
            val bw_it = bws_set.iterator()
            while (latency_it.hasNext()) {
              val entry =
                latency_it.next().asInstanceOf[Map.Entry[String, Double]]
              latencies = latencies :+ new Latency(
                entry.getKey().toInt,
                entry.getValue().toFloat
              )
            }
            while (bw_it.hasNext()) {
              val entry =
                bw_it.next().asInstanceOf[Map.Entry[String, Double]]
              bws = bws :+ new BW(
                entry.getKey().toInt,
                entry.getValue().toFloat
              )
            }
            // TODO: Setting value of NumTasksDeployed
            taskMgrCfg.append(
              new TaskManagerInfo(
                i,
                json_cfg.get("numSlots").asInstanceOf[Long].asInstanceOf[Int],
                0,
                latencies,
                bws,
                json_cfg.get("ipRate").asInstanceOf[Double].asInstanceOf[Float],
                json_cfg.get("opRate").asInstanceOf[Double].asInstanceOf[Float]
              )
            )
          }
          taskMgrCfgs.append(taskMgrCfg)
        }

      } catch {
        case e: Throwable => e.printStackTrace()
      }
      var i: Int = 0
      while (true) {
        reconfigurationManager.solveILP(
          taskMgrCfgs(i).asInstanceOf[ArrayBuffer[TaskManagerInfo]],
          1.0.toFloat
        )
        i = (i + 1) % (taskMgrCfgs.length)
        Thread.sleep(5000)
      }
    }
  }
  // .start()

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

  def runJob(ops: Array[String], parallelisms: Array[Int]): Boolean = {
    val totalParallelism = parallelisms.sum
    // Call ILP solver with totalParallelism

    val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)

    if (ps == null) {
      println("Cannot create optimal execution plan.")
      return false
    }

    // Create execution plan
    val plan = ExecutionPlan.createPlan(
      taskManagers,
      ps,
      ops,
      parallelisms,
      taskIDCounters
    )

    // Record job
    jobs += new Job(ops, parallelisms, plan)

    // Communicate tasks to TMs
    assignTasks(plan, ops)
    
    jobIDCounter += 1 // increment job counter
    true
  }

  def assignTasks(plan: Array[ArrayBuffer[(Int, Int)]], ops: Array[String]) = {
    // Use plan to assign tasks to TMs
    for (row <- plan.indices) {
      plan(row).foreach {
        case (tm, taskID) => {
          val from = if (row == 0) { Array.empty[Int] }
          else {
            plan(row - 1).map(x => taskManagers(x._1).id).toArray
          }
          val to = if (row == plan.length - 1) { Array.empty[Int] }
          else {
            plan(row + 1).map(x => taskManagers(x._1).id).toArray
          }
          val toTaskIDs = if (row == plan.length - 1) { Array.empty[Int] }
          else {
            plan(row + 1).map(x => taskManagers(x._2).id).toArray
          }
          val tmi = Naming
            .lookup("taskmanager" + taskManagers(tm).id)
            .asInstanceOf[TaskManagerInterface]
          tmi.assignTask(
            new Task(
              jobIDCounter,
              taskID,
              from,
              to,
              toTaskIDs,
              operator = if (row > 0) ops(row - 1) else "data"
            )
          )
        }
      }
    }
  }

  def replanJob(jobID: Int): Boolean = {
    val job: Job = jobs(jobID)

    val totalParallelism = job.parallelisms.sum
    // Call ILP solver with totalParallelism
    val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)

    if (ps == null) {
      println("Cannot create optimal execution plan.")
      return false
    }

    // Create new execution plan
    val newPlan = ExecutionPlan.createPlan(
      taskManagers,
      ps,
      job.ops,
      job.parallelisms,
      taskIDCounters
    )

    // Idea: calculate new plan as combination between old and new plan
    // Simply assign all tasks in combined plan.
    // Task manager still running the taskID only update the to, toTaskIDs and from properties.
    // This means a TaskSlot should remove itself from the TM when it finishes!

     val combinedPlan = Array.fill(newPlan.length)(ArrayBuffer.empty[(Int, Int)])
     val oldPlan = job.plan

    //oldplan map1: [(0, 0), (0, 1)]
    //newplan map1: [(0, 2), (1, 0), (1, 1)]
    //combinedmap1: [(0, 0), (1, 0), (1, 1)]

     for (op <- newPlan.indices) { // for each operator
       for (i <- newPlan(op).indices) { // for each assignment
         var matchFound = false
         var j = 0 // iterator for oldPlan
         while (j < oldPlan(op).length && !matchFound) { // search for assignment to same TM in old plan
           if (oldPlan(op)(j)._1 == newPlan(op)(i)._1) { // if scheduled for same TM
             combinedPlan(op) += oldPlan(op)(j) // use assignment from old plan
             oldPlan(op).remove(j) // prevent matching with this old assignment again
             matchFound = true // break the loop, and prevent using assignment from new plan
           }
         }
         if (!matchFound) { // if oldPlan doesn't contain any more assignments for the same TM, add the assignment from new plan
           combinedPlan(op) += newPlan(op)(i)
         }
       }
     }

     // Re-assign the tasks
     assignTasks(combinedPlan, job.ops)
     true
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
case class Job(
    ops: Array[String],
    parallelisms: Array[Int],
    plan: Array[ArrayBuffer[(Int, Int)]]
)
