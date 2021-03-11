// package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.rmi.registry.LocateRegistry;
import executionplan._

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
    val TaskManager = new TaskManager(id)
    registry.bind("taskmanager" + id, TaskManager)

    println("TaskManager " + id + " bound!")
  }
}

class TaskManager(val id: Int)
    extends UnicastRemoteObject
    with TaskManagerInterface {
  def assignTask(task: Task): Unit = {
    // Create socket connection with task.from and task.to
    // And start executing the operator on the inputstream and piping it to outputstream
  }
}
