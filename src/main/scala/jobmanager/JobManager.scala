package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.io.FileReader
import java.util.Map
import scala.collection.mutable.ArrayBuffer
import taskmanager._
import scala.util.Random
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
        var x = true
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
                json_cfg.get("opRate").asInstanceOf[Double].asInstanceOf[Float],
                0
              )
            )
          }

          // TODO: TEMP REMOVE THIS
          if (x){
            for (i <- taskMgrCfg){
              taskManagers.append(i)
            }
            x = false
          }
          taskMgrCfgs.append(taskMgrCfg)
        }

      } catch {
        case e: Throwable => e.printStackTrace()
      }
      // var i: Int = 0
      // while (true) {
      //   reconfigurationManager.solveILP(
      //     taskMgrCfgs(i).asInstanceOf[ArrayBuffer[TaskManagerInfo]],
      //     1.0.toFloat
      //   )
      //   i = (i + 1) % (taskMgrCfgs.length)
      //   Thread.sleep(5000)
      // }
    }
  }
  .start()

  /**
    * Assign a TaskManager an unique id an return it to him. 
    */
  def register(): Int = {
    taskManagerIdCounter += 1
    // taskManagers.append(
    //   new TaskManagerInfo(
    //     id = taskManagerIdCounter,
    //     0,
    //     0,
    //     Array.empty[Latency],
    //     Array.empty[BW],
    //     0,
    //     0,
    //     0
    //   )
    // )
    taskIDCounters += 0 // initialize task counter to 0
    return taskManagerIdCounter;
  }

  def runJob(ops: Array[String], parallelisms: Array[Int]): Boolean = {
    val totalParallelism = parallelisms.sum
    // Call ILP solver with totalParallelism

    // val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)
    val ps = Array(3, 2, 1)

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

    println("original plan")
    // Print plan
    for (op <- plan.indices) {
      println (plan(op).mkString(" "))
    }
    
    // Record job
    jobs += new Job(ops, parallelisms, plan)
    
    // Communicate tasks to TMs
    val tasks = assignTasks(plan, ops)
    
    // Assign input and output rates for each taskManager based on the execution plan
    assignRates(taskManagers, plan, tasks)

    jobIDCounter += 1 // increment job counter
    true
  }

  /**
    * Assigns the tasks from a plan object to the TMs. 
    */
  def assignTasks(plan: Array[ArrayBuffer[(Int, Int)]], ops: Array[String]) : scala.collection.mutable.Map[(Int, Int), (Int, Array[Int], Array[Int])] = {
    val tasks = scala.collection.mutable.Map[(Int, Int), (Int, Array[Int], Array[Int])]()

    // Use plan to assign tasks to TMs
    for (op <- plan.indices) {
      plan(op).foreach {
        case (tm, taskID) => {
          var from = Array.empty[Int]
          var to = Array.empty[Int]
          var toTaskIDs = Array.empty[Int]
                                                                            // Set "from" if this is not the first operator (data)
          if (op != 0) { 
            from = plan(op - 1).map(x => taskManagers(x._1).id).toArray     // set tmIDs of previous operator
          }
                                                                            // Set "to" and "toTaskIDs" if this is not the last operator (sink)
          if (op != plan.length - 1) {
            to = plan(op + 1).map(x => taskManagers(x._1).id).toArray        // set tmIDs of next operator
            toTaskIDs = plan(op + 1).map(x => taskManagers(x._2).id).toArray // set taskIDs of next operator
          }
          val tmi = Naming
            .lookup("taskmanager" + taskManagers(tm).id)
            .asInstanceOf[TaskManagerInterface]
          val task = new Task(
            jobIDCounter,
            taskID,
            from,
            to,
            toTaskIDs,
            operator = if (op > 0) ops(op - 1) else "data"
          )
          tasks += (((tm, taskID), (tm, from, to)))
          tmi.assignTask(task)
        }
      }
    }
    
    return tasks
  }


  // Idea: calculate new plan as combination between old and new plan
  // Simply assign all tasks in combined plan.
  // Task manager still running the taskID only update the to, toTaskIDs and from properties.
  // This means a TaskSlot should remove itself from the TM when it finishes!
  def replanJob(jobID: Int): Boolean = {
    val job: Job = jobs(jobID)

    val totalParallelism = job.parallelisms.sum
    // Call ILP solver with totalParallelism
    // val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)
    val ps = Array(2, 2, 2)

    if (ps == null) {
      println("Cannot create optimal execution plan.")
      return false
    }

    // Create new execution plan
    val newPlan = ExecutionPlan.createPlan(taskManagers, ps, job.ops, job.parallelisms, taskIDCounters)
    val combinedPlan = Array.fill(newPlan.length)(ArrayBuffer.empty[(Int, Int)])
    val oldPlan = job.plan

    println("old plan")
    ExecutionPlan.printPlan(oldPlan)
    println("new plan")
    ExecutionPlan.printPlan(newPlan)

     for (op <- newPlan.indices) {                       // for each operator in new plan
       for (i <- newPlan(op).indices) {                  // for each assignment in new plan
         var matchFound = false
         var j = 0 // iterator for oldPlan
         while (j < oldPlan(op).length && !matchFound) { // search for assignment to same TM in old plan
           if (oldPlan(op)(j)._1 == newPlan(op)(i)._1) { // if scheduled for same TM
             combinedPlan(op) += oldPlan(op)(j)          // use assignment from old plan
             oldPlan(op).remove(j)                       // prevent matching with this old assignment again
             matchFound = true                           // break the loop
           }
           j += 1
         }
         if (!matchFound) { // if oldPlan doesn't contain any more assignments for the same TM, add the assignment from new plan
           combinedPlan(op) += newPlan(op)(i)
         }
       }
     }
     println("combined plan")
     ExecutionPlan.printPlan(combinedPlan)
     
     // TODO: exclude the data operator from termination/suspension?
     
     // terminate remaining oldPlan tasks
     for (op: ArrayBuffer[(Int, Int)] <- oldPlan) {
        for (as: (Int, Int) <- op) {
          val tm = Naming.lookup("taskmanager" + taskManagers(as._1).id).asInstanceOf[TaskManagerInterface]
          tm.terminateTask(jobID, taskID = as._2)
        }
     }
     // suspend all combinedPlan tasks (unknown tasks will be ignored by the TMs)
     for (op: ArrayBuffer[(Int, Int)] <- combinedPlan) {
        for (as: (Int, Int) <- op) {
          val tm = Naming.lookup("taskmanager" + taskManagers(as._1).id).asInstanceOf[TaskManagerInterface]
          tm.suspendTask(jobID, taskID = as._2)
        }
     }

    // Re-assign the tasks (suspended tasks will get resumed)
     assignTasks(combinedPlan, job.ops)
     true
  }

  // Assign the input and output rates to the tms based on the execution plan and metrics
  def assignRates(taskManagers: ArrayBuffer[TaskManagerInfo], plan: Array[ArrayBuffer[(Int, Int)]],  tasks: scala.collection.mutable.Map[(Int, Int), (Int, Array[Int], Array[Int])]) = {
    for (i <- taskManagers) {
      println (i.id)
      println (i.bandwidthsToSelf.mkString(", "))
    }

    val dataSource = 3

    // Data source λ_O is equal to the bandwiths
    for (assignment <- plan(0)) {
      // sum of all bandwiths from data source to outgoing tms
      val taskLists = tasks.get(assignment._1, assignment._2)   
      for (i <- taskLists.get._3) {
        taskManagers(dataSource).opRate += taskManagers(i).bandwidthsToSelf(dataSource).rate
      }
    }

    // Next operators excluding the sink
    for (i <- 1 to plan.size - 1) {
      plan(i).foreach(assignment => {
        val taskLists = tasks.get(assignment._1, assignment._2)
       
        // Minimum of the sum of bandwidths of the From-list and the sum of opRate of the From-list
        // TODO: Check if we have to calculate all the opRates first and then calculate the ipRates!
        var bwSum : Float = 0
        var opSum : Float = 0
        taskLists.get._2.foreach(tm => {bwSum += taskManagers(assignment._1).bandwidthsToSelf(tm).rate
          opSum += taskManagers(tm).opRate})

        taskManagers(assignment._1).ipRate = bwSum.min(opSum) 

        // Assign the min of the ipRate and a randomly generated number to the processing rate
        taskManagers(assignment._1).prRate = taskManagers(assignment._1).ipRate.min(Random.nextInt(1000))
        println("prRate " + assignment._1 + ": " + taskManagers(assignment._1).prRate)
        
        // Assign min of prRate and sum of outgoing bandwidths to the opRate
        var sum : Float = 0
        taskLists.get._3.foreach(tm => sum += taskManagers(tm).bandwidthsToSelf(assignment._1).rate)
        println("sum: " + sum)
        taskManagers(assignment._1).opRate = taskManagers(assignment._1).prRate.min(sum)
        println("opRate " + assignment._1 + ": " + taskManagers(assignment._1).opRate)

      })
    }

    // Rates of the sink
    val assignment = plan(plan.size - 1)(0)
    val taskLists = tasks.get(assignment._1, assignment._2)
    var bwSum : Float = 0
    var opSum : Float = 0
    taskLists.get._2.foreach(tm => {bwSum += taskManagers(assignment._1).bandwidthsToSelf(tm).rate
      opSum += taskManagers(tm).opRate})

    for (tm <- taskManagers) {
      println("ID: " + tm.id + " IP: " + tm.ipRate + " OP: " + tm.opRate)
    }
    
  }
}

case class Latency(var fromID: Int, var time: Float)
case class BW(var fromID: Int, var rate: Float)
case class Job(
    ops: Array[String],
    parallelisms: Array[Int],
    plan: Array[ArrayBuffer[(Int, Int)]]
)
