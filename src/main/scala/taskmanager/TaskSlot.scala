package taskmanager

import java.net.Socket
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import scala.collection.mutable.ArrayBuffer
import jobmanager.Task
import java.net.SocketException
import jobmanager.BW
import java.rmi.Naming
import jobmanager.JobManagerInterface

class TaskSlot(val tmID: Int) extends Runnable {

  var task: Task = null
  var from: ArrayBuffer[DataInputStream] = ArrayBuffer.empty[DataInputStream]
  var to: ArrayBuffer[DataOutputStream] = ArrayBuffer.empty[DataOutputStream]
  val bottleneckSimVal = 9000
  var bws: Array[Int] = null

  @volatile var terminateFlag: Boolean = false // used to terminate the sink task

  var state: Int = 0

  def run(): Unit = {
    if (task.operator.equals("data")) {
      data()
    }
    else if (task.operator.equals("map")) {
      map()
    }
    else if (task.operator.equals("reduce")) {
      reduce()
    }
  }

  def data(): Unit = {
    printWithID("Data...")
    // Generate data
    val amountOfData = 10000 - state // remove data that has already been sent
    printWithID("Amount of data left: " + amountOfData)
    val data = Array.fill[Int](amountOfData)(2) // 2 2 2 ... -> 3 3 3 ... -> 4 4 4  ... -> 4+4+4 ... -> 4000000

    // write one value for each outputstream in a loop
    var outputIndex = 0
    for (i <- data.indices) {
      try {
        // Simulate actual bandwidth
        Thread.sleep((bottleneckSimVal / bws(outputIndex)).max(1)) // sleep at least 1ms
        to(outputIndex).writeInt(data(i))
        state += 1 // record how many elements have been sent so far
        outputIndex = (outputIndex + 1) % to.length
      }
      catch {
        case se: SocketException => {
          cleanup()
          return
        }
      }
    }
    cleanup()
  }

  def map(): Unit = {
    printWithID("Map...")
    var counter = 0
    var inputIndex = 0
    var outputIndex = 0
    while (from.length > 0) {
      try {
        outputIndex = (outputIndex + 1) % to.length
        inputIndex = (inputIndex + 1) % from.length

        var value: Int = from(inputIndex).readInt()
        value += 1
        counter += 1
        // Simulate actual bandwidth
        Thread.sleep((bottleneckSimVal / bws(outputIndex)).max(1)) // sleep at least 1ms
        if ((counter % 1000) == 0) {
          printWithID("read: " + counter)
        }
        to(outputIndex).writeInt(value)
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        }
        case se: SocketException => {
          cleanup()
          return
        }
      }
    }
    // All inputstreams have reached EOF
    printWithID("Finished Map")
    cleanup()
  }

  def reduce(): Unit = {
    printWithID("Reduce...")
    var inputIndex = 0
    while (from.length > 0) {

      if (this.terminateFlag) {
        printWithID("TERMINATED")
        cleanup()
        return
      }

      try {
        inputIndex = (inputIndex + 1) % from.length // try next inputstream

        var value: Int = from(inputIndex).readInt()
        state += value
      }
      catch {
        case eof: EOFException => { // expected, end of stream reached
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        }
        case se: SocketException => {
          cleanup()
          return
        }
      }
    }
    // No more input streams
    printWithID("Finished Reduce")

    // If this is the sink
    if (to.length == 0) {
      Naming.lookup("jobmanager").asInstanceOf[JobManagerInterface].reportResult(state)
    }
    else {
      var index: Int = 0
      for (out <- to) {
        // Simulate actual bandwidth
        Thread.sleep((bottleneckSimVal / bws(index)).max(1)) // sleep at least 1ms
        index += 1
        out.writeInt(state)
        out.flush()
      }
      cleanup()
    }
  }

  // Close all socket connections
  def cleanup() = {
    for (out <- to) {
      out.flush()
      out.close()
    }
    for (in <- from) {
      in.close()
    }
    from.clear()
    to.clear()
    this.terminateFlag = false
    this.task = null
  }

  def stop() = {
    this.terminateFlag = true
  }

  def printWithID(msg: String) = {
    if (task != null) {
      println("(TM_" + tmID + ", task " + task.taskID + ", op " + task.operator + "): " + msg)
    }
    else {
      println("(TM_" + tmID + "): " + msg)
    }
  }

}
