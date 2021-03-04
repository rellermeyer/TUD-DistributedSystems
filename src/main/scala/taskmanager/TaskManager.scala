package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import executionplan._

class TaskManager(val id: Int, registryURL: String) extends UnicastRemoteObject with TaskManagerInterface {
  // TODO: put local monitor stuff here (bandwidth, latency, processing rate, ...)

// http://192.168.1.5:2000/taskmanager2
  Naming.bind(registryURL + id, this);
  myPrint("Added to registry")

  val port = 8000 + id;
  val serverSocket = new ServerSocket(port)
  myPrint("Listening on port: " + port)

  def assignTask[A, B](task : Task[A, B]): Unit = {
    // TODO: implement
    myPrint("Started task")
    
    var x: A = false

    val ar = Array(1, 2, 3, 4)
    (task.operator) match  {
      case "map" => ar.map(task.fun)
    }

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
