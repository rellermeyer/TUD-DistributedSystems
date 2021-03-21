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

  val taskSlots = scala.collection.mutable.Map[String, TaskSlot]()

  // ServerSocket used for INCOMING data
  val portOffset = 9000
  val port = 9000 + id
  val serverSocket = new ServerSocket(port)
  println("Server socket started on port: " + port)

  /**
    * Listens for socket connections from upstream TaskSlots.
    * First reads jobID and taskID to match with task received in assignTask() 
    */
  new Thread {
    override def run {
      while (true) {
        val inputSocket = serverSocket.accept()
        val inputStream = new DataInputStream(inputSocket.getInputStream())
        val jobID = inputStream.readInt() // jobID communicated through socket
        val taskID = inputStream.readInt() // taskID communicated through socket
        println(
          "Connected inputsocket for (jobID, taskID): (" + jobID + ", " + taskID + ")"
        )
        var taskSlot = getTaskSlot(jobID, taskID)
        // add the inputstream to the taskslot
        taskSlot.from += new DataInputStream(inputSocket.getInputStream())
        // try to run the task slot
        runTaskSlot(jobID, taskID)
      }
    }
  }.start()

  /**
    * rmi call from JobManager. Creates socket connections to downstream TaskSlots.
    */
  def assignTask(task: Task): Unit = {
    println(
      "Received task for (jobID, taskID): (" + task.jobID + ", " + task.taskID + ")"
    )

    var taskSlot = getTaskSlot(task.jobID, task.taskID)
    taskSlot.task = task

    // Set output streams (If any. Could also be sink)
    for (i <- task.to.indices) {
      val outputSocket = new Socket("localhost", portOffset + task.to(i))
      val dos = new DataOutputStream(outputSocket.getOutputStream())
      // let receiver know the jobID and HIS corresponding taskID
      dos.writeInt(task.jobID)
      dos.writeInt(
        task.toTaskIDs(i)
      )
      taskSlot.to += dos // add the outputstream to the taskslot
    }
    // Try to run the task slot.
    runTaskSlot(task.jobID, task.taskID)
  }

  /**
    * Runs the TaskSlot if all inputs and outputs are connected.
    */
  def runTaskSlot(jobID: Int, taskID: Int) = synchronized {
    val slot = getTaskSlot(jobID, taskID)

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
  def getTaskSlot(jobID: Int, taskID: Int): TaskSlot = synchronized {
    val key = jobID + "" + taskID
    var taskSlot = taskSlots.getOrElse(key, null)

    if (taskSlot == null) {
      println(
        "Creating new taskslot for (jobID, taskID): (" + jobID + ", " + taskID + ")"
      )
      taskSlot = new TaskSlot(key)
      taskSlots.put(key, taskSlot)
    }
    return taskSlot
  }
}

case class TaskManagerInfo(
    id: Int,
    var numSlots: Int,
    var numTasksDeployed: Int,
    var latenciesToSelf: Array[Latency],
    var bandwidthsToSelf: Array[BW],
    var ipRate: Float,
    var opRate: Float
)
