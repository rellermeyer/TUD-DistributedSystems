package main.scala.network

import java.io.IOException
import java.net.ServerSocket

import scala.reflect.ClassTag

/**
 * The Server part of the network module -
 * Listens for new connections to the node in a thread
 * @param address - The address of the node
 */
class Server[T: ClassTag](val address: Address, modelCallback: T => Unit) extends Thread {
  override def run(): Unit = {
    println("Server start up")
    val server = new ServerSocket(address.port)
    while(true) {
      try {
        val socket = Some(server.accept())
        //    Init the server thread to maintain the connection
        new ServerThread(socket.get, modelCallback).start()
      } catch {
        case e: IOException => println("Server error: " + e)
      }
    }
  }

  this.start()
}
