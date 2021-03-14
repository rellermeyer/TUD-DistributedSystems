package taskmanager

import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
class TaskSlot(val jobID: Int) extends Runnable {

  var inputSocket: Socket = null
  var outputSocket: Socket = null
  var operator: String = null

  def run(): Unit = {
    println("running taskslot for jobID " + jobID)

    if (operator.equals("data")) {
      println("operator data")
      val data = Array.fill[Int](5)(2)
      val outputStream = new DataOutputStream(outputSocket.getOutputStream())
      for (i <- data.indices) {
        outputStream.writeInt(data(i))
      }
      outputStream.flush()
      outputSocket.close()
      return
    }

    val inputStream = new DataInputStream(inputSocket.getInputStream())
    
    if (operator.equals("map")) {
      println("operator map")
      val outputStream = new DataOutputStream(outputSocket.getOutputStream())
      try {
        while (true) {
          var value: Int = inputStream.readInt()
          value = value + 1
          // apply all bottlenecks here
          outputStream.writeInt(value)
        }
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          println("EOF")
          outputStream.flush()
          inputSocket.close()
          outputSocket.close()
        }
      }
    }
    else if (operator.equals("reduce")) {
      println("operator reduce")
      var sum = 0
      try {
        while (true) {
          var value: Int = inputStream.readInt()
          sum = sum + value
          // apply computational bottleneck here
        }
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          println("EOF")
          // if not sink
          if (outputSocket != null) {
            println("write reduce result to outputsocket")
            val outputStream = new DataOutputStream(outputSocket.getOutputStream())
            outputStream.writeInt(sum)
            outputStream.flush()
            outputSocket.close()
          }
          else { // sink, just print result for now
            inputSocket.close()
            println("Result: " + sum)
          }
        }
      }
    }
  }

  def applyBottleneck() = {
      val time = 1000
      Thread.sleep(time)
  }
}
