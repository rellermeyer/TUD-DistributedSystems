// package taskmanager

import java.net.ServerSocket
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.InputStreamReader
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import java.rmi.registry.LocateRegistry;
import executionplan._
import scala.util.Random
import java.net.Socket
import java.io.DataOutputStream
import java.io.DataInputStream
import taskmanager.TaskSlot

object TaskManagerRunner {
  /*
   * Create a new JVM instance for each Task Manager (via a new terminal invocation of it)
   * Lookup the Job Manager's RMI registry, call its register() function to receive a unique id,
   *  and register with that id (bind to the registry)
   *
   * Terminal command `sbt "runMain taskmanager.TaskManager"`
   */
  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry(1099)

    val jobManagerName = "jobmanager"
    val jobManager: JobManagerInterface =
      registry.lookup(jobManagerName).asInstanceOf[JobManagerInterface]

    val id = jobManager.register() // get id from JobManager
    val taskManager = new TaskManager(id)
    val taskManagerName = "taskmanager" + id
    registry.bind(taskManagerName, taskManager)

    println("TaskManager " + id + " bound!")

    sys.ShutdownHookThread {
      println("Unregistering taskmanager" + id)
      registry.unbind(taskManagerName)
    }

    val rand = new Random

    // TODO: Make non static for number of registered task managers
    val no_taskManagers = 4 // exclude JobManager

    var latencies = new Array[Latency](no_taskManagers)
    var bws = new Array[BW](no_taskManagers)

    // Simulate some random bandwidth and latency
    for (i <- 0 until no_taskManagers) {
      latencies(i) = new Latency(
        i,
        Random.nextFloat() * 3 // max 3 seconds latency
      )
      if (i != id) {
        bws(i) = new BW(i, 500 + Random.nextFloat() * 3000)
      } else {
        bws(i) = new BW(i, 0)
      }
    }

    jobManager.monitorReport(
      id,
      2, // upper bound exclusive, so max 2 slots
      latencies,
      bws,
      rand.nextInt(1000),
      rand.nextInt(500)
    )
  }
}

class TaskManager(val id: Int)
    extends UnicastRemoteObject
    with TaskManagerInterface {

  var availableTaskSlots = 2 // TODO: Actually use this
  val taskSlots = scala.collection.mutable.Map[String, TaskSlot]()

  // ServerSocket used for INCOMING data
  val portOffset = 9000
  val port = 9000 + id
  val serverSocket = new ServerSocket(port)
  println("Server socket started on port: " + port)

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

  def assignTask(task: Task): Unit = {
    println(
      "Received task for (jobID, taskID): (" + task.jobID + ", " + task.taskID + ")"
    )

    var taskSlot = getTaskSlot(task.jobID, task.taskID)
    taskSlot.task = task

    // Set output streams (if any)
    for (i <- task.to.indices) {
      val outputSocket = new Socket("localhost", portOffset + task.to(i))
      val dos = new DataOutputStream(outputSocket.getOutputStream())
      // let receiver know the jobID and HIS corresponding taskID
      dos.writeInt(task.jobID)
      dos.writeInt(
        task.toTaskIDs(i)
      ) // NOTE: this is the taskID for the RECEIVER
      // add the outputstream to the taskslot
      taskSlot.to += dos
    }
    // try to run the task slot
    runTaskSlot(task.jobID, task.taskID)
  }

  def runTaskSlot(jobID: Int, taskID: Int) = synchronized {
    val slot = getTaskSlot(jobID, taskID)

    // check if both inputs and outputs are connected
    if (
      slot.task != null &&
      slot.task.from.length == slot.from.length &&
      slot.task.to.length == slot.to.length
    ) {
      new Thread(slot).start()
    }
  }

  def getTaskSlot(jobID: Int, taskID: Int): TaskSlot = synchronized {
    val key = jobID + "" + taskID
    // try to find already existing one
    var taskSlot = taskSlots.getOrElse(key, null)

    if (taskSlot == null) {
      // create new one
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
