package helper

import java.io._
import java.net.Socket

import scala.concurrent.{ExecutionContext, Future}

/**
  * Object that implement transfering files via socket boilerplate code.
  * Should be used in order to avoid repeating socket code.
  */
object socketHelper {

  /**
    * Method for sending objects only via Socket connection
    *
    * @param conn
    * @param objectToSend
    * FIXME Does not actually work
    */
  def send(conn: Socket, objectToSend: Serializable): Unit = {
    val output = conn.getOutputStream
    new ObjectOutputStream(output).writeObject(objectToSend)

    val byteArray = new Array[Byte](4 * 1024)

    Future {
      output.write(byteArray)

      output.flush()
    }(ExecutionContext.global)
  }

  /**
    * Method for sending objects with files via Socket connection.
    *
    * @param conn target socket connection
    * @param objectToSend
    * @param fileInputStream
    * FIXME Does not actually work
    */
  def send(conn: Socket, objectToSend: Serializable, fileInputStream: FileInputStream): Unit = {
    val input = new BufferedInputStream(fileInputStream)
    val output = conn.getOutputStream

    new ObjectOutputStream(output).writeObject(this)

    val byteArray = new Array[Byte](4 * 1024)

    Future {
      var len = input.read(byteArray)
      while (len != -1) {
        output.write(byteArray, 0, len)
        len = input.read(byteArray)
      }
      output.flush()
    }(ExecutionContext.global)
  }

}
