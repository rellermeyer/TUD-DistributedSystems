package jobmanager

import java.rmi.registry.LocateRegistry;
import taskmanager.TaskManager
import scala.annotation.switch

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    /*
     *  Create global registry for the JobManager and TaskManagers to connect to.
     */
    val registryPort = 1099
    val registry = LocateRegistry.createRegistry(registryPort)
    println("Registry running on port " + registryPort)

    // Second cmd line arguement indicates if replanning or not (-noreplan for no replanning, -replan for repalanning; default replanning)
    var replan: Boolean = true
    args(1) match {
      case "-replan" => replan = true
      case "-noreplan" => replan = false
      case _ => replan = true
    }

    val jobManager = new JobManager(replan)
    val jobManagerName = "jobmanager"

    registry.bind(jobManagerName, jobManager)
    println("JobManager bound!")

    // Launch some Task Managers if specified in args
    if (args.length > 1) {
      val no_tms = Integer.parseInt(args(0))
      for (i <- 0 until no_tms) {
        val id = jobManager.register() // get id from JobManager
        val taskManager = new TaskManager(id)
        val taskManagerName = "taskmanager" + id

        registry.bind(taskManagerName, taskManager)
        println("TaskManager " + id + " bound!")
        }
    }
    else {
        println("No additional taskmanagers started")
    }

    // sys.ShutdownHookThread {
    //   println("Unregistering JobManager")
    //   registry.unbind(jobManagerName)
    // }
  }
}
