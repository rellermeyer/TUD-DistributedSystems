// package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.rmi.registry.LocateRegistry;
import executionplan._
import scala.util.Random

object TaskManagerRunner {
  /*
   * Create a new JVM instance for each Task Manager (via a new terminal invocation of it)
   * Lookup the Job Manager's RMI registry, call its register() function to receive a unique id,
   *  and register with that id (bind to the registry)
   *
   * Terminal command `sbt "runMain taskmanager.TaskManager"`
   */
  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry(1098)

    val jobManagerName = "jobmanager"
    val jobManager: JobManagerInterface =
      registry.lookup(jobManagerName).asInstanceOf[JobManagerInterface]

    val id = jobManager.register() // get id from JobManager
    val TaskManager = new TaskManager(id)
    val taskManagerName = "taskmanager" + id
    registry.bind(taskManagerName, TaskManager)

    println("TaskManager " + id + " bound!")

    sys.addShutdownHook {
      registry.unbind(taskManagerName)
    }

    val rand = new Random

    val no_taskManagers = registry.list().length - 1 // exclude JobManager

    var latencies = new Array[Latency](no_taskManagers)
    var bws = new Array[BW](no_taskManagers)

    // Simulate some random bandwidth and latency
    for (i <- 0 until no_taskManagers) {
      latencies(i) = new Latency(
        i,
        Random.nextFloat() * 3 // max 3 seconds latency
      )
      if (i != id) {
        bws(i) = new BW(i, Random.nextFloat() * 3)
      }
    }

    jobManager.monitorReport(
      id,
      rand.nextInt(3), // upper bound exclusive, so max 2 slots
      latencies,
      bws,
      rand.nextInt(1000),
      rand.nextInt(500)
    )
  }
}

class TaskManager(val id: Int)
    extends UnicastRemoteObject
    with TaskManagerInterface {

  def assignTask(task: Task): Unit = {
    // Create socket connection with task.from and task.to
    // And start executing the operator on the inputstream and piping it to outputstream
    // val outputSocket =
  }

  def getAllTaskMgrIDs() {}

  def myPrint(text: String) {
    println(id + ": " + text)
  }

  // ServerSocket used for INCOMING data
  val port = 8000
  val serverSocket = new ServerSocket(port)
  myPrint("Server socket started on port: " + port)

  // new Thread {
  //   while (true) {
  //     // accept() blocks execution until a client connects.
  //     // this socket is only used for INCOMING data!!
  //     val inputSocket = serverSocket.accept()

  //     val outputStream = new PrintWriter(inputSocket.getOutputStream(), true) // not sure we need this
  //     val inputStream = new InputStreamReader(inputSocket.getInputStream())
  //   }
  // }.start()

}
