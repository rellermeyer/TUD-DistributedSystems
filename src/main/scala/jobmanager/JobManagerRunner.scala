package jobmanager

import java.rmi.registry.LocateRegistry;
import taskmanager.TaskManager

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    /*
     *  Create global registry for the JobManager and TaskManagers to connect to.
     */
    val registryPort = 1099
    val registry = LocateRegistry.createRegistry(registryPort)
    println("Registry running on port " + registryPort)

    val jobManager = new JobManager
    val jobManagerName = "jobmanager"

    registry.bind(jobManagerName, jobManager)
    println("JobManager bound!")

    // Launch some Task Managers if specified in args
    if (args.length == 1) {
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
