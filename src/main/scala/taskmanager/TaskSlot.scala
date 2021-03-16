package taskmanager

import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import scala.collection.mutable.ArrayBuffer
import executionplan.Task

class TaskSlot(val key: String) extends Runnable {

  var task: Task = null
  var from: ArrayBuffer[DataInputStream] = ArrayBuffer.empty[DataInputStream]
  var to: ArrayBuffer[DataOutputStream] = ArrayBuffer.empty[DataOutputStream]

  def run(): Unit = {
    println("Running Taskslot for (jobID, taskID): (" + task.jobID + ", " + task.taskID + ")")

    if (task.operator.equals("data")) {
      data()
    }
    else if (task.operator.equals("map")) {
      map()
    }
    else if (task.operator.equals("reduce")) {
      reduce()
    }
    println("Task finished")
  }

  def data() = {
    println("Data...")
    // Generate data
    val data = Array.fill[Int](10000)(2)

    // write one value for each outputstream in a loop
    var outputIndex = 0
    for (i <- data.indices) {
      to(outputIndex).writeInt(data(i))
      outputIndex = (outputIndex + 1) % to.length
    }
    cleanup()
  }

  def map() = {
    println("Map...")
    var inputIndex = 0
    var outputIndex = 0
    while (from.length > 0) {
      try {
        outputIndex = (outputIndex + 1) % to.length
        inputIndex = (inputIndex + 1) % from.length

        var value: Int = from(inputIndex).readInt()
        value += 1
        to(outputIndex).writeInt(value)
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        }
      }
    }
    // All inputstreams have reached EOF
    cleanup()
  }

  def reduce() = {
    println("Reduce...")
    var sum = 0
    var inputIndex = 0
    while (from.length > 0) {
      try {
        inputIndex = (inputIndex + 1) % from.length // try next inputstream

        var value: Int = from(inputIndex).readInt()
        sum += value
        // apply all bottlenecks here
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        }
      }
    }
    // Reduce should have at most 1 outputstream!!!!! Write correct execution plans!!!!
    if (to.length > 0) {
      to(0).writeInt(sum)
      to(0).flush()
      to(0).close()
    }
    else { // sink
      println("Result: " + sum)
    }
  }

  def cleanup() = {
    // Flush and close all outputstreams
    for (i <- to.indices) {
      to(i).flush()
      to(i).close()
    }
  }

}
