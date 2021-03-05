package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.rmi.registry.LocateRegistry;
import jobmanager.JobManagerInterface
import executionplan._

object TaskManager extends UnicastRemoteObject with TaskManagerInterface {
  /*
   * Create a new JVM instance for each Task Manager (via a new terminal invocation of it)
   * lookup the Job Manager's RMI registry, check the number of already registered Task
   * Managers to get an id, and register with that id (bind to the registry)
   * 
   * Terminal command `sbt "runMain taskmanager.TaskManager"`
   */

  // TODO: put local monitor stuff here (bandwidth, latency, processing rate, ...)
  
  var id = 0 // Initialized by the return value of the rmi call to JobManager

  def main(args: Array[String]) = {
    val registry = LocateRegistry.getRegistry(1099)
    println(registry.list.mkString);

    val jobManagerName = "jobmanager"
    val jobManager: JobManagerInterface = registry.lookup(jobManagerName).asInstanceOf[JobManagerInterface]
    println("Found JobManager!")
    id = jobManager.register() // get id from JobManager
    println("Registered id")
    Naming.bind("rmi://localhost:1099/taskmanager", this) // http://192.168.1.5:2000/taskmanager2
    println("Bound to registry")
  }
}
