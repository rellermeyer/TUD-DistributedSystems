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

class JobManager(taskMgrsCount: Int, replan: Boolean)
    extends UnicastRemoteObject
    with JobManagerInterface {
  var taskManagerIdCounter = -1
  var taskManagers = ArrayBuffer[TaskManagerInfo]()
  val reconfigurationManager = ReconfigurationManager

  val taskIDCounters = ArrayBuffer.empty[Int] // for each TM a task counter

  var job: Job = null // gets initialized in runJob()
  var numReplans: Int = 0

  val taskMgrCfgs = parseConfigs()

  def startConfigThread() = {
    new Thread {
      override def run {
        var i: Int = 0
        while (job != null) {
          // Overwrite rate values from config file with current rates
          for (j <- 0 until taskManagers.length) {
            taskMgrCfgs(i)(j).ipRate = taskManagers(j).ipRate
            taskMgrCfgs(i)(j).opRate = taskManagers(j).opRate
            taskMgrCfgs(i)(j).prRate = taskManagers(j).prRate
          }
          taskManagers = taskMgrCfgs(i)
          if (replan) {
            replanJob()
          } else {
            // In case there is no replanning still assign new rates to the task managers
            assignRates(job.plan)
            broadcastMetadata(job.plan)
          }
          i = (i + 1) % (taskMgrCfgs.length) // go to next config
          Thread.sleep(10000)
        }
      }
    }.start()
  }

  /** 
   * Assign a TaskManager an unique id an return it to him.
    */
  def register(): Int = {
    taskManagerIdCounter += 1
    taskIDCounters += 0 // initialize task counter to 0
    return taskManagerIdCounter;
  }

  // Inform the task managers about their processing rate and bandwidths
  def broadcastMetadata(plan: Array[ArrayBuffer[(Int, Int)]]): Unit = {
    for (op <- plan.indices) {
      for ((tm, taskID) <- plan(op)) {
        val to =
          if (op != plan.length - 1) plan(op + 1).map(_._1).toArray
          else Array.empty[Int]
        val bws = to.map(taskManagers(_).bandwidthsToSelf(tm).rate.toInt)
        val tmi =
          Naming.lookup("taskmanager" + tm).asInstanceOf[TaskManagerInterface]
        tmi.receiveMetadata(taskID, bws, taskManagers(tm).prRate)
      }
    }
  }

  def runJob(
      ops: Array[String],
      parallelisms: Array[Int],
      dataSize: Int
  ): Boolean = {
    // Create some initial plan so that the rates for all task managers can be calculated and used for created the actual plan
    var remaining = parallelisms.sum
    val initialPS: Array[Int] = new Array(taskManagers.length)
    for (index <- 0 until remaining) {
      initialPS(index % initialPS.length) += 1
    }
    val initialPlan = ExecutionPlan.createPlan(
      taskManagers,
      initialPS,
      ops,
      parallelisms,
      taskIDCounters
    )
    assignRates(initialPlan)

    // Call ILP solver with sum of parallelisms
    val ps = ReconfigurationManager.solveILP(taskManagers, parallelisms.sum)

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
    ExecutionPlan.printPlan(plan)

    // Record job
    job = new Job(ops, parallelisms, plan, System.currentTimeMillis(), -1)

    // Assign input and output rates for each taskManager based on the execution plan
    assignRates(plan)
    broadcastMetadata(plan)

    // Communicate tasks to TMs
    assignTasks(plan, ops, dataSize)

    // Record timestamp here
    job.queryStartTime = System.currentTimeMillis()

    startConfigThread()

    return true
  }

  /** 
   * Assigns the tasks from a plan object to the TMs.
    */
  def assignTasks(
      plan: Array[ArrayBuffer[(Int, Int)]],
      ops: Array[String],
      dataSize: Int
  ) = {
    for (op <- plan.indices) {
      val task: Task = generateTask(plan, ops, op)
      for ((tm, taskID) <- plan(op)) {
        task.taskID = taskID
        val tmi =
          Naming.lookup("taskmanager" + tm).asInstanceOf[TaskManagerInterface]
        tmi.assignTask(
          task,
          if (op == 0) dataSize
          else
            0 // For the data operator, give it the initial data size as specified in the Query. Else 0.
        )
      }
    }
  }

  /** 
   * Helper function for assignTasks() and reassignTasks()
    */
  def generateTask(
      plan: Array[ArrayBuffer[(Int, Int)]],
      ops: Array[String],
      row: Int
  ): Task = {
    var from = Array.empty[Int]
    var to = Array.empty[Int]
    var toTaskIDs = Array.empty[Int]

    if (row != 0) { // Set "from" if this is not the first operator (data)
      from = plan(row - 1)
        .map(x => taskManagers(x._1).id)
        .toArray // Use tmIDs of previous operator
    }
    if (row != plan.length - 1) { // Set "to" and "toTaskIDs" if this is not the last operator (sink)
      to = plan(row + 1)
        .map(x => taskManagers(x._1).id)
        .toArray // Use tmIDs of next operator
      toTaskIDs =
        plan(row + 1).map(_._2).toArray // Use taskIDs of next operator
    }
    val operator =
      if (row > 0) ops(row - 1)
      else
        "data" // ops (from the user query) does not contain the "data" op, so we need to add it here
    return new Task(-1, from, to, toTaskIDs, operator)
  }

  /** 
   * Called periodically after the bandwidth and processing rate of the system has changed.
    */
  def replanJob(): Boolean = {
    if (job == null) {
      return false
    }
    // Call ILP solver with totalParallelism
    val ps = ReconfigurationManager.solveILP(taskManagers, job.parallelisms.sum)
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
    ) // will be transformed into migrateTo
    val oldPlan = job.plan // will be transformed into migrateFrom
    val same = Array.fill(newPlan.length)(
      ArrayBuffer.empty[(Int, Int)]
    ) // subset of oldPlan that does not need to be migrated
    val combinedPlan = Array.fill(newPlan.length)(
      ArrayBuffer.empty[(Int, Int)]
    ) // concatenation of same and migrateTo

    println("old plan")
    ExecutionPlan.printPlan(oldPlan)
    println("new plan")
    ExecutionPlan.printPlan(newPlan)

    // Only re-assign if execution plans are different
    if (!ExecutionPlan.equalPlans(oldPlan, newPlan)) {
      numReplans += 1
      // Halt all current executions
      for (op <- oldPlan) {
        for ((tm, taskID) <- op) {
          val tmi = Naming
            .lookup("taskmanager" + tm)
            .asInstanceOf[TaskManagerInterface]
          tmi.terminateTask(taskID)
        }
      }
      Thread.sleep(300) // allow the system to terminate completely

      // Halt all current executions by stopping the sink, and letting the upstream neighbors react to the emerging SocketExceptions
      // val sink = oldPlan(oldPlan.length - 1)(0)
      // val tm = Naming
      //   .lookup("taskmanager" + sink._1)
      //   .asInstanceOf[TaskManagerInterface]
      // tm.terminateTask(taskID = sink._2)

      // Thread.sleep(1000) // allow the system to terminate completely

      // Construct <migrateFrom> and <migrateTo>, by removing assignments that are present in both old and new plans.
      // Add these duplicates to <same>
      for (op <- newPlan.indices) {
        var i = 0
        while (i < newPlan(op).length) {
          var matchFound = false
          var j = 0

          while (j < oldPlan(op).length && !matchFound) {
            if (oldPlan(op)(j)._1 == newPlan(op)(i)._1) {
              same(op) += oldPlan(op)(j)
              oldPlan(op).remove(j)
              newPlan(op).remove(i)
              i -= 1 // move one step back, because we just removed something from this list
              matchFound = true
            }
            j += 1
          }
          i += 1
        }
      }
      // combinedPlan is the actual plan that will be executed, but we only need it to fetch the <to>s when assigning the tasks from the three components, <migrateFrom>, <migrateTo> and <same>
      for (i <- oldPlan.indices) {
        combinedPlan(i) = same(i) ++ newPlan(i)
      }
      job.plan = combinedPlan // Update the job's plan for future replanning

      // println("same")
      // ExecutionPlan.printPlan(same)

      // println("migrateFrom")
      // ExecutionPlan.printPlan(oldPlan)

      // println("migrateTo")
      // ExecutionPlan.printPlan(newPlan)

      // println("combined")
      // ExecutionPlan.printPlan(combinedPlan)

      // Assign input and output rates for each taskManager based on the new execution plan
      assignRates(combinedPlan)
      broadcastMetadata(combinedPlan)

      // Re-assign the tasks
      reassignTasks(same, oldPlan, newPlan, combinedPlan, job.ops)

    } else {
      println("---- Equal plans, no rescheduling! ----")
    }

    return true
  }

  /** 
   * Same as assignTasks(), but calls TaskManager.migrate() instead of TaskManager.assignTask() for some of the assignments.
    */
  def reassignTasks(
      same: Array[ArrayBuffer[(Int, Int)]],
      migrateFrom: Array[ArrayBuffer[(Int, Int)]],
      migrateTo: Array[ArrayBuffer[(Int, Int)]],
      combined: Array[ArrayBuffer[(Int, Int)]],
      ops: Array[String]
  ): Unit = {
    for (op <- combined.indices) {
      val task = generateTask(combined, ops, op)

      // Assign all the <same> assignments (they will be resumed)
      for ((tm, taskID) <- same(op)) {
        task.taskID = taskID
        val tmi =
          Naming.lookup("taskmanager" + tm).asInstanceOf[TaskManagerInterface]
        tmi.assignTask(
          task,
          -1
        ) // initial state -1 indicates the TS should use the old state
      }

      // Communicate to all taskslots in <migrateFrom> to migrate their task to the corresponding task in <migrateTo>
      for (as <- migrateFrom(op).indices) {
        val (tmFrom, taskIDFrom) = migrateFrom(op)(as)
        val (tmTo, taskIDTo) = migrateTo(op)(as)

        task.taskID = taskIDTo
        val tmi = Naming
          .lookup("taskmanager" + tmFrom)
          .asInstanceOf[TaskManagerInterface]
        tmi.migrate(taskIDFrom, (tmTo, taskIDTo), task)
      }
    }
  }

  /** 
   * Assign the input and output rates to the tms based on the execution plan and metrics
    */
  def assignRates(plan: Array[ArrayBuffer[(Int, Int)]]) = {
    val dataSource = ExecutionPlan.dataSource
    val tasks =
      scala.collection.mutable.Map[(Int, Int), (Int, Array[Int], Array[Int])]()

    // genereate hashmap of to/from lists for all tms
    for (op <- plan.indices) {
      for ((tm, taskID) <- plan(op)) {
        var from = Array.empty[Int]
        var to = Array.empty[Int]

        if (op != 0) {
          from = plan(op - 1).map(x => taskManagers(x._1).id).toArray
        }

        if (op != plan.length - 1) {
          to = plan(op + 1).map(x => taskManagers(x._1).id).toArray
        }
        tasks += (((tm, taskID), (tm, from, to)))
      }
    }

    // Data source λ_O is equal to the bandwidths
    for (assignment <- plan(0)) {
      // sum of all bandwidths from data source to outgoing tms
      val taskLists = tasks.get(assignment._1, assignment._2)
      for (i <- taskLists.get._3) {
        taskManagers(dataSource).opRate += taskManagers(i)
          .bandwidthsToSelf(dataSource)
          .rate
        taskManagers(dataSource).ipRate = 0
      }
      taskManagers(
        dataSource
      ).opRate /= taskLists.get._3.length // divide by the amount of parallelism in next operator
    }

    // Next operators excluding the sink
    for (i <- 1 to plan.size - 1) {
      plan(i).foreach(assignment => {
        val taskLists: Option[(Int, Array[Int], Array[Int])] =
          tasks.get(assignment._1, assignment._2)

        // Minimum of the sum of bandwidths of the From-list and the sum of opRate of the From-list
        var bwSum: Float = 0
        var opSum: Float = 0
        taskLists.get._2.foreach(tm => {
          bwSum += taskManagers(assignment._1).bandwidthsToSelf(tm).rate
          opSum += taskManagers(tm).opRate
        })

        taskManagers(assignment._1).ipRate = bwSum.min(opSum)

        // Assign the min of the ipRate and a randomly generated number to the processing rate
        // taskManagers(assignment._1).prRate =
        //   taskManagers(assignment._1).ipRate.min(Random.nextInt(1000))

        // DONT RECALCULATE PRRATE

        // Assign min of prRate and sum of outgoing bandwidths to the opRate
        var sum: Float = 0
        taskLists.get._3.foreach(tm =>
          sum += taskManagers(tm).bandwidthsToSelf(assignment._1).rate
        )
        taskManagers(assignment._1).opRate =
          taskManagers(assignment._1).prRate.min(sum)
        if (taskLists.get._3.length > 0) {
          taskManagers(assignment._1).opRate /= taskLists.get._3.length
        }
      })
    }

    // Rates of the sink
    val assignment = plan(plan.size - 1)(0)
    val taskLists = tasks.get(assignment._1, assignment._2)
    var bwSum: Float = 0
    var opSum: Float = 0
    taskLists.get._2.foreach(tm => {
      bwSum += taskManagers(assignment._1).bandwidthsToSelf(tm).rate
      opSum += taskManagers(tm).opRate
    })
  }

  /** 
   * Called by a sink TaskSlot to report the result of the query.
    */
  def reportResult(result: Int): Unit = {
    job.totalTime = System.currentTimeMillis() - job.queryStartTime
    println("FINISHED JOB: " + result)
    println("Total RunTime: " + job.totalTime + " ms")
    println("Number of Replans: " + numReplans)
    numReplans = 0
    job = null // prevent replanning
  }

  def parseConfigs(): ArrayBuffer[ArrayBuffer[TaskManagerInfo]] = {
    val jsonParser = new JSONParser()
    var taskMgrCfgs = ArrayBuffer[ArrayBuffer[TaskManagerInfo]]()
    try {
      val reader = new FileReader("config-12.json")
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
          taskMgrCfg.append(
            new TaskManagerInfo(
              i,
              json_cfg.get("numSlots").asInstanceOf[Long].asInstanceOf[Int],
              latencies,
              bws,
              json_cfg
                .get("ipRate")
                .asInstanceOf[Double]
                .asInstanceOf[Float],
              json_cfg
                .get("opRate")
                .asInstanceOf[Double]
                .asInstanceOf[Float],
              json_cfg
                .get("prRate")
                .asInstanceOf[Double]
                .asInstanceOf[Float]
            )
          )
        }
        taskMgrCfgs.append(taskMgrCfg)
      }
      taskManagers = taskMgrCfgs(0)
      return taskMgrCfgs
    } catch {
      case e: Throwable => { e.printStackTrace(); return null }
    }
  }
}

case class Latency(var fromID: Int, var time: Float)
case class BW(var fromID: Int, var rate: Float)
case class Job(
    ops: Array[String],
    parallelisms: Array[Int],
    var plan: Array[ArrayBuffer[(Int, Int)]],
    var queryStartTime: Long,
    var totalTime: Long
)
