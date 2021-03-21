package taskmanager

import java.rmi.registry.LocateRegistry;
import jobmanager.JobManagerInterface

object TaskManagerRunner {
  /*
   * Create a new JVM instance for each Task Manager (via a new terminal invocation of it)
   * Lookup the Job Manager's RMI registry, call its register() function to receive a unique id,
   *  and register with that id (bind to the registry)
   *
   * Terminal command `sbt "runMain taskmanager.TaskManager"`
   */
  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry(1099)

    val jobManagerName = "jobmanager"
    val jobManager: JobManagerInterface =
      registry.lookup(jobManagerName).asInstanceOf[JobManagerInterface]

    val id = jobManager.register() // get id from JobManager
    val taskManager = new TaskManager(id)
    val taskManagerName = "taskmanager" + id
    
    registry.bind(taskManagerName, taskManager)
    println("TaskManager " + id + " bound!")

    // sys.ShutdownHookThread {
    //   println("Unregistering taskmanager" + id)
    //   registry.unbind(taskManagerName)
    // }
  }
}
