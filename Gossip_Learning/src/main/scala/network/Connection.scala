package main.scala.network

import java.io.{IOException, ObjectOutputStream}
import java.net.{Socket, UnknownHostException}

/**
 * Socket connection to a neighbour
 * @param neighbourAddress - The address of the neighbour
 */
class Connection[T](val neighbourAddress: Address) {
  var socket: Option[Socket] = None
  var objectOutputStream: Option[ObjectOutputStream] = None

//  timeOut for reconnect when disconnected
  val timeOut: Long = 20000
//  delay of the Heartbeat
  val heartBeatDelayMillis:Long = 5000

//  Keep track of connection
  var connected = false;
  var tryReconnect = true;

//  Thread for maintaining the connection
  val connectionThread:Thread = new Thread() {
    override def run(): Unit = {
      connect()

//      Connection loop
      while (tryReconnect) {
//        If connected, check connection with heartbeat
        if (connected) {
          try {
            println("Heartbeat for " + neighbourAddress)
            objectOutputStream.get.writeObject(StatusCodes.Heartbeat)
            Thread.sleep(heartBeatDelayMillis)
          } catch {
            case e: InterruptedException =>
              println("Error and stopping thread: " + e)
              tryReconnect = false
              connected = false
            case e: IOException =>
              println("Server offline: " + neighbourAddress.toString)
              connected = false
          }
        } else {
          connect()
        }
      }
    }
  }

  connectionThread.start()

  /**
   * Set-up the connection to the neighbour Server
   */
  def connect(): Unit = {
    try {
      socket = Some(new Socket(this.neighbourAddress.host, this.neighbourAddress.port))
      objectOutputStream = Some(new ObjectOutputStream(socket.get.getOutputStream))
      println("Socket " + this.neighbourAddress + " is connected!")
      connected = socket.get.isConnected
    } catch {
      case e: UnknownHostException =>
        println("ERROR: " + e)
        Thread.sleep(timeOut)
      case e: IOException =>
        println("ERROR IO for " + this.neighbourAddress + ": " + e)
        Thread.sleep(timeOut)
    }
  }

  /**
   * Shutdown the connection gracefully
   */
  def shutdown(): Unit = {
    tryReconnect = false
    objectOutputStream.get.close()
    socket.get.close()
  }

  /**
   * Send a model update request to the neighbour Server
   * @param model - The network communication model object
   */
  def pushModel(model: T): Unit = {
    if (connected) {
      try {
        objectOutputStream.get.writeObject(model)
        println("Sending model: " + model.toString)
      } catch {
        case e: IOException => println("Error sending Model: " + e)
      }

    }
  }
}