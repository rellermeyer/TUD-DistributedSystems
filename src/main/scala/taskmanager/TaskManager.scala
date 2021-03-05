package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.rmi.registry.LocateRegistry;
import jobmanager._
import executionplan._

class TaskManager extends UnicastRemoteObject with TaskManagerInterface {
  /*
   * Create a new JVM instance for each Task Manager (via a new terminal invocation of it)
   * lookup the Job Manager's RMI registry, check the number of already registered Task
   * Managers to get an id, and register with that id (bind to the registry)
   * 
   * Terminal command `sbt "runMain taskmanager.TaskManager"`
   */

  // TODO: put local monitor stuff here (bandwidth, latency, processing rate, ...)
  
  private var id = 0 // Initialized by the return value of the rmi call to JobManager

  def main(args: Array[String]) = {
    val registryURL = "rmi://localhost:1099"
    val jobManager: JobManagerInterface = Naming.lookup(registryURL).asInstanceOf[JobManagerInterface]
    id = jobManager.register() // get id from JobManager
    Naming.bind("taskmanager" + id, this) // http://192.168.1.5:2000/taskmanager2
    myPrint("Added to registry")

    val port = 8000 + id;
    val serverSocket = new ServerSocket(port)
    myPrint("Listening on port: " + port)

  
  }
  
  def assignTask[A, B](task : Task[A, B]): Unit = {
      // TODO: implement
      myPrint("Started task")

      // while (true) {
      //     // accept() blocks execution until a client connects
      //     val clientSocket = serverSocket.accept()
      //     // outputStream to client
      //     val outputStream = new PrintWriter(clientSocket.getOutputStream(), true)
      //     // inputStream to receive data from client
      //     val inputStream = new InputStreamReader(clientSocket.getInputStream())
      // }
    }

  // better name?
  def myPrint(text: String) {
    println(id + ": " + text)
  }
}
