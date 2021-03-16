package taskmanager

import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import scala.collection.mutable.ArrayBuffer
class TaskSlot(val key: String) extends Runnable {

  // All these properties should be set by the TaskManager before running

  var from: ArrayBuffer[DataInputStream] = ArrayBuffer.empty[DataInputStream]
  var to: ArrayBuffer[DataOutputStream] = ArrayBuffer.empty[DataOutputStream]

  // Used to wait for all incoming socket connections
  // Run condition is fromCount == from.length
  var fromCount = -1 

  var operator: String = null

  def run(): Unit = {
    println("running taskslot for key " + key)

    if (operator.equals("data")) {
      println("operator data")
      val data = Array.fill[Int](10)(2) // array of 10 times the value 2

      // write one value for each outputstream in a loop
      var outputIndex = 0
      for (i <- data.indices) {
        to(outputIndex).writeInt(data(i))
        outputIndex = (outputIndex + 1) % to.length
      }
      for (i <- to.indices) {
        to(i).flush()
        to(i).close()
      }
      return
    }

    if (operator.equals("map")) {
      println("operator map")

      var inputIndex = 0
      var outputIndex = 0
      while (from.length > 0) {
        try {
          var value: Int = from(inputIndex).readInt()
          value = value + 1
          // apply all bottlenecks here

          to(outputIndex).writeInt(value)

          outputIndex = (outputIndex + 1) % to.length
          inputIndex = (inputIndex + 1) % from.length
        }
        catch {
          case eof: EOFException => { // expected, end of stream reached
            from(inputIndex).close() // close the stream
            from.remove(inputIndex) // remove inputstream from consideration
            inputIndex = (inputIndex + 1) % from.length // try next inputstream
          }
        }
      }
      // All inputstreams have reached EOF
      for (i <- to.indices) {
        to(i).flush()
        to(i).close()
      }
    }
    else if (operator.equals("reduce")) {
      println("operator reduce")
      var sum = 0
      var inputIndex = 0
      while (from.length > 0) {
        try {
          var value: Int = from(inputIndex).readInt()
          sum += value
          // apply all bottlenecks here
        }
        catch {
          case eof: EOFException => { // expected, end of stream reached
            from(inputIndex).close() // close the stream
            from.remove(inputIndex) // remove inputstream from consideration
            inputIndex = (inputIndex + 1) % from.length // try next inputstream
          }
        }
      }
      if (to.length > 0) { // Reduce should only have 1 output!!!!! Write correct execution plans!!!!
        to(0).writeInt(sum)
        to(0).flush()
        to(0).close()
      }
      else { // sink
        println("Result: " + sum)
      }
    }
  }

  def applyBottleneck() = {
      val time = 1000
      Thread.sleep(time)
  }
}
