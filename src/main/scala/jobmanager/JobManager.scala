// package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import executionplan._
import scala.collection.mutable.ArrayBuffer
// import taskmanager._

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
  val taskMgrsCount = 4

  var jobIDCounter = 0
  val taskIDCounters = ArrayBuffer.empty[Int] // for each TM a task counter

  val jobs = ArrayBuffer.empty[Job]
  new Thread {
    override def run {
      val jsonParser = new JSONParser()
      var taskMgrCfgs = ArrayBuffer[ArrayBuffer[TaskManagerInfo](taskMgrsCount)]()
      try (FileReader reader = new FileReader("config.json"))
      {   
          //Read JSON file
          Object obj = jsonParser.parse(reader);

          JSONArray configs = (JSONArray) obj;
            
          //Iterate over configs array
          for (config <- configs) {
            val taskMgrCfg = ArrayBuffer[TaskManagerInfo](taskMgrsCount)
            for (i <- 0 until taskMgrsCount) {
              val json_cfg = config.get(i.toString)
              val latencies = Array[Latency]
              val bws = Array[BW]
              val json_latencies = json_cfg.get("latencies")
              val json_bws = json_cfg.get("bandwidth")
              for (i <- 0 until taskMgrsCount) {                
                val latency_key = json_latencies.names().getString(i)             
                val bw_key = json_bws.names().getString(i)
                latencies(i) = Latency(latency_key.toInt, json_latencies.get(latency_key))
                bws(i) = Latency(bw_key.toInt, json_latencies.get(bw_key))
              }
              // TODO: Setting value of NumTasksDeployed
              taskMgrCfg(i) = new TaskManagerInfo(
                i, json_cfg.get("numSlots"), 0, 
                latencies, bws,
                json_cfg.get("ipRate"), json_cfg.get("opRate"))                
            }
            taskMgrCfgs.append(taskMgrCfg)
          }
          
      } catch (FileNotFoundException e) {
          e.printStackTrace();
      } catch (IOException e) {
          e.printStackTrace();
      } catch (ParseException e) {
          e.printStackTrace();
      }

      var i:Int = 0
      while (true) {
        reconfigurationManager.solveILP(taskMgrCfgs[i], 1.0.toFloat)
        i = (i+1)%(taskMgrCfgs.length)
        Thread.sleep(5000)
      }
    }
  }

  
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
    jobs += new Job(ops, parallelisms, pl)

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
    jobIDCounter += 1 // increment job counter
    return true
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

    // Create execution plan
    val newPlan = ExecutionPlan.createPlan(
      taskManagers,
      ps,
      ops,
      parallelisms,
      taskIDCounters
    )

    // Compare it with old plan

    /**
     * [
     *   [(0, 0), (1, 0), (1, 1)]
     *   [(1, 2), (2, 0)]
     *   [(2, 1)]
     * ]
     * 
     * [
     *   [(0, 2), (0, 3), (1, 3)]
     *   [(2, 2), (2, 3)]
     *   [(3, 0)]
     * ]
     * 
     * r(eschedule)
     * s(tay)
     * [
     *   [s(0, 0), r(0, 3), s(1, 1)]
     *   [r(2, 2), s(2, 0)]
     *   [r(3, 0)]
     * ]
     *  
     **/

     /**
     * [
     *   [(0, 0), (1, 0), (1, 1)]
     *   [(1, 2), (2, 0)]
     *   [(2, 1)]
     * ]
     * 
     * [
     *   [(1, 3), (2, 2), (2, 3)]
     *   [(2, 4), (3, 0)]
     *   [(3, 1)]
     * ]
     * 
     * r(eschedule)
     * s(tay)
     * [
     *   [r(1, 3), r(2, 2), r(2, 3)] incorrect! should be -> [s(1, 0), r(2, 2), r(2, 3)]
     *   [r(2, 2), s(2, 0)]
     *   [r(3, 0)]
     * ]
     *  
     **/

     // Idea: calculate new plan as combination between old and new plan
     // Simply assign all tasks in combined plan.
     // Task manager still running the taskID only update the to, toTaskIDs and from properties.
     // This means a TaskSlot should remove itself from the TM when it finishes!

    //  val combinedPlan = Array.fill(plan.length)(ArrayBuffer.empty[(Int, Int)])
    //  val oldPlan = job.plan

    //  for (op <- newPlan.indices) { // for each operator
    //    for (i <- newPlan(op).indices) { // for each assignment
    //      var usedOld = false
    //      for (j <- oldPlan(op).indices) { // search for assignment to same TM in old plan
    //        if (oldPlan(op)(j)._1) { // if scheduled for same TM
    //          combinedPlan(op) += oldPlan(op)(j) // use assignment from old plan
    //          usedOld = true
    //          break
    //        }
    //      }
    //      if (!usedOld) { // if old assignment didn't use 
    //        combinedPlan(op) += newPlan(op)(j)
    //      }
    //    }
    //  }
  }

  // update metrics about a taskmanager
  // def monitorReport(
  //     id: Int,
  //     numSlots: Int,
  //     latenciesToSelf: Array[Latency],
  //     bandwidthsToSelf: Array[BW],
  //     ipRate: Int,
  //     opRate: Int
  // ) = {
  //   // counter = counter + 1
  //   println("Received report from " + id)
  //   taskManagers(id).numSlots = numSlots
  //   taskManagers(id).latenciesToSelf = latenciesToSelf
  //   taskManagers(id).bandwidthsToSelf = bandwidthsToSelf
  //   taskManagers(id).ipRate = ipRate
  //   taskManagers(id).opRate = opRate

  //   // TODO: only call it once in a while not all the time new data comes in
  //   // TODO: implement actual parallelism (need to increase or decrease, scale up or down)
  //   // if (counter == 3) {
  //   //   for (i <- taskManagers.indices) {
  //   //     println(taskManagers(i).bandwidthsToSelf.mkString(", "))
  //   //   }
  //   //   reconfigurationManager.solveILP(taskManagers, 1.0.toFloat, alpha)
  //   // }
  // }
}

case class Latency(var fromID: Int, var time: Float)
case class BW(var fromID: Int, var rate: Float)
case class Job(ops: Array[String], parallelisms: Array[Int], plan: Array[ArrayBuuffer[(Int, Int)]])
