package jobmanager

import java.rmi.registry.LocateRegistry;
import taskmanager.TaskManager
import scala.annotation.switch

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    /*
     *  Create global registry for the JobManager and TaskManagers to connect to.
     *  args: [num_tms] -[replan/noreplan]
     *    num_tms: Int for number of task managers
     *    replan/noreplan: run without or with replanning when config changes
     */
    val registryPort = 1099
    val registry = LocateRegistry.createRegistry(registryPort)
    println("Registry running on port " + registryPort)

    // TODO: Maybe parse in a nicer way
    var replan: Boolean = true
    var numTms = 1
    if (args.length > 1) {
      numTms = Integer.parseInt(args(0))
      args(1) match {
        case "-replan"   => replan = true
        case "-noreplan" => replan = false
        case _           => replan = true
      }
    }

    val jobManager = new JobManager(numTms, replan)
    val jobManagerName = "jobmanager"

    registry.bind(jobManagerName, jobManager)
    println("JobManager bound!")

    // Launch some Task Managers if specified in args
    if (args.length > 1) {
      for (i <- 0 until numTms) {
        val id = jobManager.register() // get id from JobManager
        val taskManager = new TaskManager(id)
        val taskManagerName = "taskmanager" + id

        registry.bind(taskManagerName, taskManager)
        println("TaskManager " + id + " bound!")
      }
    } else {
      println("No additional taskmanagers started")
    }

    // sys.ShutdownHookThread {
    //   println("Unregistering JobManager")
    //   registry.unbind(jobManagerName)
    // }
  }
}
