
import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader

class TaskManager(val id: Int) extends Runnable {
  // TODO: put local monitor stuff here (bandwidth, latency, processing rate, ...)


  def run(): Unit = {
    val port = 8000 + id;
    val serverSocket = new ServerSocket(port)
    println("TaskManager " + id + " listening on port: " + port)
    while (true) {
        // accept() blocks execution until a client connects
        val clientSocket = serverSocket.accept()
        // outputStream to client
        val outputStream = new PrintWriter(clientSocket.getOutputStream(), true)
        // inputStream to receive data from client
        val inputStream = new InputStreamReader(clientSocket.getInputStream())
    }
  }
}
