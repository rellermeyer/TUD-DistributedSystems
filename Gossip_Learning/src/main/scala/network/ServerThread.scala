package main.scala.network

import java.io.{IOException, InputStream, ObjectInputStream, ObjectOutputStream}
import java.net.Socket

import scala.reflect.ClassTag

/**
 * A Server thread to maintain connection with a client
 * @param socket - The corresponding socket of the connection
 */
class ServerThread[T: ClassTag](val socket: Socket, modelCallback: T => Unit) extends Thread {
  var neighbourAddress = new Address(socket.getInetAddress.toString + ":" + socket.getPort.toString)

  override def run(): Unit = {
    var inputStream: InputStream = null
    var objectInputStream: ObjectInputStream = null
    var out: ObjectOutputStream = null

    try {
//      Setup input and output streams
      inputStream = socket.getInputStream
      objectInputStream = new ObjectInputStream(inputStream)
      out = new ObjectOutputStream(socket.getOutputStream)
    } catch {
      case e:IOException =>
        println("Server error input out: " + e)
        return
    }

    while (true) {
      try {
//        Check for new input, and validate
        val inputObject: Object =  objectInputStream.readObject()
        if (inputObject != null) {
          inputObject match {
            case inputModel: T => modelCallback(inputModel)
            case statusCode: StatusCodes.Value =>
              statusCode match {
                case StatusCodes.Heartbeat => println(statusCode + " received from " + neighbourAddress.toString)
                case x => println(x + " Not implemented status code received")
              }
            case x => println("Received undefined object " + x.getClass.getName)
          }
        }
      } catch {
        case e:IOException =>
          println("Server error input out: " + e)
          return
      }
    }
  }
}