package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import scala.util.Random
import java.net.Socket
import java.io.DataOutputStream
import java.io.DataInputStream
import taskmanager.TaskSlot
import jobmanager._

class TaskManager(val id: Int)
    extends UnicastRemoteObject
    with TaskManagerInterface {

  val taskSlots = scala.collection.mutable.Map[Int, TaskSlot]() // key = taskID

  // ServerSocket used for INCOMING data
  val portOffset = 9000
  val port = 9000 + id
  val serverSocket = new ServerSocket(port)
  printWithID("Server socket started on port: " + port)

  /**
    * Listens for socket connections from upstream TaskSlots.
    * First reads taskID to match with task received in assignTask() 
    */
  new Thread {
    override def run {
      while (true) {
        val inputSocket = serverSocket.accept()
        val inputStream = new DataInputStream(inputSocket.getInputStream())
        val taskID = inputStream.readInt() // taskID communicated through socket
        var taskSlot = getTaskSlot(taskID)
        // add the inputstream to the taskslot
        taskSlot.from += new DataInputStream(inputSocket.getInputStream())
        // try to run the task slot
        runTaskSlot(taskID)
      }
    }
  }.start()

  /**
    * rmi call from JobManager. Creates socket connections to downstream TaskSlots.
    */
  def assignTask(task: Task, initialState: Int): Unit = {
    var taskSlot = getTaskSlot(task.taskID)
    taskSlot.task = task
    if (initialState != -1) { // only assign the initial state if this is a migrated task
      taskSlot.state = initialState
    }

    // Set output streams (If any. Could also be sink)
    for (i <- task.to.indices) {
      val outputSocket = new Socket("localhost", portOffset + task.to(i))
      val dos = new DataOutputStream(outputSocket.getOutputStream())
      dos.writeInt(task.toTaskIDs(i))                                   // let receiver know his corresponding taskID
      taskSlot.to += dos                                                // add the outputstream to the taskslot
    }

    runTaskSlot(task.taskID)                                            // Try to run the task slot.
  }

  /**
    * Runs the TaskSlot if all inputs and outputs are connected.
    */
  def runTaskSlot(taskID: Int) = synchronized {
    var slot = getTaskSlot(taskID)

    if (
      slot.task != null &&
      slot.task.from.length == slot.from.length &&
      slot.task.to.length == slot.to.length
    ) {
      new Thread(slot).start()
    }
  }

  /**
    * Creates a new TaskSlot and returns it, or returns an existing TaskSlot.
    */
  def getTaskSlot(taskID: Int): TaskSlot = synchronized {
    var taskSlot = taskSlots.getOrElse(taskID, null)

    if (taskSlot == null) {
      taskSlot = new TaskSlot(id)
      taskSlots.put(taskID, taskSlot)
    }
    return taskSlot
  }

  def terminateTask(taskID: Int): Unit = {
    printWithID("Terminating task " + taskID)
    val taskSlot = taskSlots.get(taskID).get                            // should always exist
    taskSlot.stop()
  }

  def migrate(taskID: Int, to: (Int, Int), task: Task): Unit = {
    val tm = Naming.lookup("taskmanager" + to._1).asInstanceOf[TaskManagerInterface]
    tm.assignTask(task, getTaskSlot(taskID).state)
  }

  def receiveMetadata(taskID: Int, bws: Array[Int], prRate: Float): Unit = {
    getTaskSlot(taskID).bws = bws
    getTaskSlot(taskID).prRate = prRate
  }

  def printWithID(msg: String) = {
    println("TM_" + id + ": " + msg)
  }

}

case class TaskManagerInfo(
    id: Int,
    var numSlots: Int,
    var latenciesToSelf: Array[Latency],
    var bandwidthsToSelf: Array[BW],
    var ipRate: Float,
    var opRate: Float,
    var prRate: Float
)
