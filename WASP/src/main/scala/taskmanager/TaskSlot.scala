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
  val bottleneckSimVal = 10000
  var bws: Array[Int] = null
  var prRate: Float = 0.0.toFloat
  var state: Int = 0
  var curThread: Thread = null

  def run(): Unit = {
    curThread = Thread.currentThread()
    if (task.operator.equals("data")) {
      data()
    } else if (task.operator.equals("map")) {
      map()
    } else if (task.operator.equals("reduce")) {
      reduce()
    }
  }

  def data(): Unit = {
    printWithID("Data...")
    printWithID(
      bws
        .map((x: Int) => (bottleneckSimVal / x).max((prRate).ceil.toInt))
        .mkString(" ")
    )
    printWithID("bws: " + bws.mkString(" "))
    printWithID("prRate: " + prRate)

    // Generate data
    printWithID("Amount of data left: " + state)
    val data = Array.fill[Int](state)(
      2
    ) // 2 2 2 ... -> 3 3 3 ... -> 4 4 4  ... -> 4+4+4 ... -> 4000000

    // write one value for each outputstream in a loop
    var outputIndex = 0
    for (i <- data.indices) {
      try {
        // Simulate actual processing rate and bandwidth
        Thread.sleep(
          (bottleneckSimVal / bws(outputIndex)).min((prRate).ceil.toInt)
        )
        to(outputIndex).writeInt(data(i))
        state -= 1 // record how many elements have been sent so far
        outputIndex = (outputIndex + 1) % to.length
      } catch {
        case _: Throwable => {
          printWithID("Terminated implicitly")
          cleanup()
          return
        }
      }
    }
    notifyEndOfStream()
    cleanup()
  }

  def map(): Unit = {
    printWithID("Map...")
    var counter = 0
    var inputIndex = 0
    var outputIndex = 0
    printWithID(
      bws
        .map((x: Int) => (bottleneckSimVal / x).max((prRate).ceil.toInt))
        .mkString(" ")
    )
    while (from.length > 0) {
      try {
        if (Thread.currentThread.isInterrupted()) {
          printWithID("interrupted")
        }
        outputIndex = (outputIndex + 1) % to.length
        inputIndex = (inputIndex + 1) % from.length

        var value: Int = from(inputIndex).readInt()

        if (value == -1) { // upstream neighbor indicates end of stream
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        } else {
          value += 1 // transform value
          counter += 1
          // Simulate actual bandwidth
          Thread.sleep(
            (bottleneckSimVal / (bws(outputIndex) * (prRate).ceil.toInt)).max(1)
          ) // sleep at least 1ms
          if ((counter % 1000) == 0) {
            printWithID("read: " + counter)
          }
          to(outputIndex).writeInt(value)
        }

      } catch {
        // EOFException if upstream neighbor closes stream
        // SocketException if downstream neighbor closes stream
        // InterruptedException if thread is interrupted during sleep
        case _: Throwable => {
          printWithID("Terminated implicitly")
          cleanup()
          return
        }
      }
    }
    // All inputstreams have indicated end of stream
    printWithID("Finished Map")
    notifyEndOfStream()
    cleanup()
  }

  def reduce(): Unit = {
    printWithID("Reduce...")
    printWithID(
      bws
        .map((x: Int) => (bottleneckSimVal / x).max((prRate).ceil.toInt))
        .mkString(" ")
    )
    var inputIndex = 0
    while (from.length > 0) {
      try {
        inputIndex = (inputIndex + 1) % from.length // try next inputstream

        var value: Int = from(inputIndex).readInt()
        if (value == -1) { // upstream neighbor indicates end of stream
          from(inputIndex).close() // close the stream
          from.remove(inputIndex) // remove inputstream from consideration
        } else {
          state += value
        }
      } catch {
        // EOFException if upstream neighbor closes stream
        // SocketException if downstream neighbor closes stream
        case _: Throwable => {
          printWithID("Terminated implicitly")
          cleanup()
          return
        }
      }
    }
    // No more input streams
    printWithID("Finished Reduce")

    // If this is the sink, report result to JobManager
    if (to.length == 0) {
      Naming
        .lookup("jobmanager")
        .asInstanceOf[JobManagerInterface]
        .reportResult(state)
    } else {
      var index: Int = 0
      for (out <- to) {
        // Simulate actual bandwidth
        Thread.sleep(
          (bottleneckSimVal / bws(index)).min((prRate).ceil.toInt)
        )
        index += 1
        out.writeInt(state)
        out.writeInt(-1) // indicate end of stream
      }
    }
    cleanup()
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
    // this.terminateFlag = false
    this.task = null
  }

  def notifyEndOfStream() = {
    for (out <- to) {
      out.writeInt(-1) // indicate end of stream
      out.flush()
      out.close()
    }
  }

  def stop() = {
    curThread.interrupt()
  }

  def printWithID(msg: String) = {
    if (task != null) {
      println(
        "(TM_" + tmID + ", task " + task.taskID + ", op " + task.operator + "): " + msg
      )
    } else {
      println("(TM_" + tmID + "): " + msg)
    }
  }

}
