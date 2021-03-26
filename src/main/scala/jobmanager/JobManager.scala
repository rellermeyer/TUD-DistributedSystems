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
  var taskManagers = ArrayBuffer[TaskManagerInfo]()
  val reconfigurationManager = ReconfigurationManager
  val taskMgrsCount = 4

  val taskIDCounters = ArrayBuffer.empty[Int] // for each TM a task counter

  var job: Job = null // gets initialized in runJob()

  // TODO: Configs should stay with the same system setup and only vary the bws and rates
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
            taskMgrCfg.append(
              new TaskManagerInfo(
                i,
                json_cfg.get("numSlots").asInstanceOf[Long].asInstanceOf[Int],
                latencies,
                bws,
                json_cfg.get("ipRate").asInstanceOf[Double].asInstanceOf[Float],
                json_cfg.get("opRate").asInstanceOf[Double].asInstanceOf[Float],
                0
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
        taskManagers = taskMgrCfgs(i)
        //  assign the new config to the taskmanagers and call replan (that will then solve ILP)
        Thread.sleep(10000)

        // TODO: For benchmarking: If baseline running don't replan when config changes, for optimized replan when configs changes
        while (!replanJob())
          Thread.sleep(5000)
        i = (i + 1) % (taskMgrCfgs.length)
      }
    }
  }
  .start()

  /**
    * Assign a TaskManager an unique id an return it to him. 
    */
  def register(): Int = {
    taskManagerIdCounter += 1
    taskIDCounters += 0 // initialize task counter to 0
    return taskManagerIdCounter;
  }

  def runJob(ops: Array[String], parallelisms: Array[Int]): Boolean = {
    val totalParallelism = parallelisms.sum
    // Call ILP solver with totalParallelism

    val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)
    // val ps = Array(3, 2, 1)

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
    job = new Job(ops, parallelisms, plan)
    
    // Communicate tasks to TMs
    val tasks = assignTasks(plan, ops)
    
    // Assign input and output rates for each taskManager based on the execution plan
    assignRates(taskManagers, plan, tasks)

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
            to = plan(op + 1).map(x => taskManagers(x._1).id).toArray       // set tmIDs of next operator
            toTaskIDs = plan(op + 1).map(_._2).toArray                      // set taskIDs of next operator
          }
          val tmi = Naming.lookup("taskmanager" + tm).asInstanceOf[TaskManagerInterface]
          val task = new Task(
            taskID,
            from,
            to,
            toTaskIDs,
            operator = if (op > 0) ops(op - 1) else "data"
          )
          tasks += (((tm, taskID), (tm, from, to)))
          var bws = new Array[Int](to.length)
          var index : Int = 0
          for (i <- to) {
            bws(index) = taskManagers(i).bandwidthsToSelf(tm).rate.toInt
            index += 1
          }
          tmi.assignTask(task, 0, bws)
        }
      }
    }
    
    return tasks
  }

  def replanJob(): Boolean = {
    if (job == null) {
      return false
    }

    val totalParallelism = job.parallelisms.sum
    // Call ILP solver with totalParallelism
    val ps = ReconfigurationManager.solveILP(taskManagers, totalParallelism)
    // val ps = Array(2, 2, 2)

    if (ps == null) {
      println("Cannot create optimal execution plan.")
      return false
    }

    // Create new execution plan
    val newPlan = ExecutionPlan.createPlan(taskManagers, ps, job.ops, job.parallelisms, taskIDCounters) // will become migrateTo
    val oldPlan = job.plan // will become migrateFrom
    val same = Array.fill(newPlan.length)(ArrayBuffer.empty[(Int, Int)]) // subset of oldPlan that does not need to be migrated
    val combinedPlan = Array.fill(newPlan.length)(ArrayBuffer.empty[(Int, Int)]) // concatenation of same and migrateTo

    println("old plan")
    ExecutionPlan.printPlan(oldPlan)
    println("new plan")
    ExecutionPlan.printPlan(newPlan)
        
    // Only re-assign if execution plans are different
    if (!ExecutionPlan.comparePlans(oldPlan, newPlan)) {

      // Halt all current executions by stopping the sink, and letting the upstream neighbors react to the emerging SocketExceptions
      val sink = oldPlan(oldPlan.length - 1)(0)
      println("sink : " + sink)
      val tm = Naming.lookup("taskmanager" + sink._1).asInstanceOf[TaskManagerInterface]
      tm.terminateTask(taskID = sink._2)

      Thread.sleep(1000) // allow the system to terminate completely

      // Construct combinedPlan (newPlan, but use old assignments where possible)
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
              i -= 1 // prevent skipping over an assignment
              matchFound = true
            }
            j += 1
          }
          i += 1
        }
      }
      for (i <- oldPlan.indices) {
        combinedPlan(i) = same(i) ++ newPlan(i)
      }
      job.plan = combinedPlan // Update the job's plan for future replanning

      println("same")
      ExecutionPlan.printPlan(same)

      println("migrateFrom")
      ExecutionPlan.printPlan(oldPlan)

      println("migrateTo")
      ExecutionPlan.printPlan(newPlan)

      println("combined")
      ExecutionPlan.printPlan(combinedPlan)

      // Re-assign the tasks
      reassignTasks(same, oldPlan, newPlan, combinedPlan, job.ops)
    }
    else {
      println("---- Equal plans ----")
    }
    true
  }

  def reassignTasks(same: Array[ArrayBuffer[(Int, Int)]], migrateFrom: Array[ArrayBuffer[(Int, Int)]], migrateTo: Array[ArrayBuffer[(Int, Int)]], combined: Array[ArrayBuffer[(Int, Int)]], ops: Array[String]): Unit = {
    for (op <- combined.indices) {
      // Calculate the <from> and <to> based on the combined plan
      var from = Array.empty[Int]
      var to = Array.empty[Int]
      var toTaskIDs = Array.empty[Int]
                                                                        // Set "from" if this is not the first operator (data)
      if (op != 0) { 
        from = combined(op - 1).map(x => taskManagers(x._1).id).toArray     // set tmIDs of previous operator
      }
                                                                        // Set "to" and "toTaskIDs" if this is not the last operator (sink)
      if (op != combined.length - 1) {
        to = combined(op + 1).map(x => taskManagers(x._1).id).toArray       // set tmIDs of next operator
        toTaskIDs = combined(op + 1).map(_._2).toArray                      // set taskIDs of next operator
      }
      // Assign all the <same> assignments (they will be resumed)
      for ((tm, taskID) <- same(op)) {
        val tmi = Naming.lookup("taskmanager" + tm).asInstanceOf[TaskManagerInterface]
        val task = new Task(
          taskID,
          from,
          to,
          toTaskIDs,
          operator = if (op > 0) ops(op - 1) else "data"
        )
        var bws = new Array[Int](to.size)
        var index: Int = 0
        for (i <- to) {
          bws(index) = taskManagers(i).bandwidthsToSelf(tm).rate.toInt
          index += 1
        }
        tmi.assignTask(task, -1, bws) // initial state -1 indicates the TS should use the old state
      }
      for (as <- migrateFrom(op).indices) {
        val (tmFrom, taskIDFrom) = migrateFrom(op)(as)
        val (tmTo, taskIDTo) = migrateTo(op)(as)
        var bws = new Array[Int](to.size)
        var index: Int = 0
        for (i <- to) {
          bws(index) = taskManagers(i).bandwidthsToSelf(tmTo).rate.toInt
          index += 1
        }

        val tmi = Naming.lookup("taskmanager" + tmFrom).asInstanceOf[TaskManagerInterface]
        tmi.migrate(taskIDFrom, (tmTo, taskIDTo), new Task(
          taskIDTo,
          from,
          to,
          toTaskIDs,
          operator = if (op > 0) ops(op - 1) else "data",
        ), bws)
      }
    }
  }

  def reportResult(result: Int): Unit = {
    println ("FINISHED JOB: " + result)
    job = null // prevent replanning
  }

  // Assign the input and output rates to the tms based on the execution plan and metrics
  def assignRates(taskManagers: ArrayBuffer[TaskManagerInfo], plan: Array[ArrayBuffer[(Int, Int)]],  tasks: scala.collection.mutable.Map[(Int, Int), (Int, Array[Int], Array[Int])]) = {
    for (i <- taskManagers) {
      println (i.id)
      println (i.bandwidthsToSelf.mkString(", "))
    }

    val dataSource = 3 // should correspond with the static data source in ExecutionPlan

    // Data source λ_O is equal to the bandwidths
    for (assignment <- plan(0)) {
      // sum of all bandwidths from data source to outgoing tms
      val taskLists = tasks.get(assignment._1, assignment._2)   
      for (i <- taskLists.get._3) {
        taskManagers(dataSource).opRate += taskManagers(i).bandwidthsToSelf(dataSource).rate
        taskManagers(dataSource).ipRate = 0
      }
      taskManagers(dataSource).opRate /= taskLists.get._3.length // divide by the amount of parallelism in next operator
    }

    // Next operators excluding the sink
    for (i <- 1 to plan.size - 1) {
      plan(i).foreach(assignment => {
        val taskLists: Option[(Int, Array[Int], Array[Int])] = tasks.get(assignment._1, assignment._2)
       
        // Minimum of the sum of bandwidths of the From-list and the sum of opRate of the From-list
        // TODO: Check if we have to calculate all the opRates first and then calculate the ipRates!
        var bwSum : Float = 0
        var opSum : Float = 0
        taskLists.get._2.foreach(tm => {bwSum += taskManagers(assignment._1).bandwidthsToSelf(tm).rate
          opSum += taskManagers(tm).opRate})

        taskManagers(assignment._1).ipRate = bwSum.min(opSum) 

        // Assign the min of the ipRate and a randomly generated number to the processing rate
        taskManagers(assignment._1).prRate = taskManagers(assignment._1).ipRate.min(Random.nextInt(1000))
        
        // Assign min of prRate and sum of outgoing bandwidths to the opRate
        var sum : Float = 0
        taskLists.get._3.foreach(tm => sum += taskManagers(tm).bandwidthsToSelf(assignment._1).rate)
        taskManagers(assignment._1).opRate = taskManagers(assignment._1).prRate.min(sum)
        if (taskLists.get._3.length > 0) {
          taskManagers(assignment._1).opRate /= taskLists.get._3.length
        }
      })
    }

    // Rates of the sink
    val assignment = plan(plan.size - 1)(0)
    val taskLists = tasks.get(assignment._1, assignment._2)
    var bwSum : Float = 0
    var opSum : Float = 0
    taskLists.get._2.foreach(tm => {bwSum += taskManagers(assignment._1).bandwidthsToSelf(tm).rate
      opSum += taskManagers(tm).opRate})
  }
}

case class Latency(var fromID: Int, var time: Float)
case class BW(var fromID: Int, var rate: Float)
case class Job(
    ops: Array[String],
    parallelisms: Array[Int],
    var plan: Array[ArrayBuffer[(Int, Int)]]
)
