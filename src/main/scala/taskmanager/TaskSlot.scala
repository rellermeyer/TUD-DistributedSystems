package taskmanager

import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import scala.collection.mutable.ArrayBuffer
import jobmanager.Task

class TaskSlot(val key: String) extends Runnable {

  var task: Task = null
  var from: ArrayBuffer[DataInputStream] = ArrayBuffer.empty[DataInputStream]
  var to: ArrayBuffer[DataOutputStream] = ArrayBuffer.empty[DataOutputStream]

  @volatile var terminateFlag: Boolean = false

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

    // TODO: REMOVE THIS TASKSLOT FROM THE TASKMANAGER! IMPORTANT FOR REPLANNING!
  }

  def data() = {
    println("Data...")
    // Generate data
    val data = Array.fill[Int](1000000)(2) // 2 2 2 ... -> 3 3 3 ... -> 4 4 4  ... -> 4+4+4 ... -> 4000000

    // write one value for each outputstream in a loop
    var outputIndex = 0
    for (i <- data.indices) {
      to(outputIndex).writeInt(data(i))
      if (this.terminateFlag) {
        println("SUSPENDED")
        cleanup() // but only once
      }
      while (this.terminateFlag) { // suspend output
        Thread.sleep(100)
      }
      outputIndex = (outputIndex + 1) % to.length
    }
    cleanup()
  }

  def map(): Unit = {
    println("Map...")
    var inputIndex = 0
    var outputIndex = 0
    while (from.length > 0) {
      
      if (this.terminateFlag) {
        println("TERMINATED")
        return // break the execution completely
      }
      try {
        outputIndex = (outputIndex + 1) % to.length
        inputIndex = (inputIndex + 1) % from.length

        var value: Int = from(inputIndex).readInt()
        value += 1
        to(outputIndex).writeInt(value)
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          if (terminateFlag) {
            cleanup()
            return
          }
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        }
      }
    }
    // All inputstreams have reached EOF
    cleanup()
  }

  def reduce(): Unit = {
    println("Reduce...")
    var sum: Int = 0
    var inputIndex = 0
    while (from.length > 0) {
      if (this.terminateFlag) {
        println("TERMINATED")
        return // break the execution completely
      }
      try {
        inputIndex = (inputIndex + 1) % from.length // try next inputstream

        var value: Int = from(inputIndex).readInt()
        sum += value
        // apply all bottlenecks here
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          if (terminateFlag) {
            cleanup()
            return
          }
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        }
      }
    }
    // Reduce should have at most 1 outputstream!!!!! Write correct execution plans!!!!
    if (to.length == 1) {
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

  def stop() = {
    this.terminateFlag = true
  }

  def resume() = {
    this.terminateFlag = false
  }

}
