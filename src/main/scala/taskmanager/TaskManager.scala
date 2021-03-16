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
      jobManager.unregister(id)
      registry.unbind(taskManagerName)
    }

    val rand = new Random

    // TODO: Make non static for number of registered task managers
    val no_taskManagers = 3 // exclude JobManager

    var latencies = new Array[Latency](no_taskManagers)
    var bws = new Array[BW](no_taskManagers)

    // Simulate some random bandwidth and latency
    for (i <- 0 until no_taskManagers) {
      latencies(i) = new Latency(
        i,
        Random.nextFloat() * 3 // max 3 seconds latency
      )
      if (i != id) {
        bws(i) = new BW(i, Random.nextFloat() * 3)
      }
      else {
        bws(i) = new BW(i, 0)
      }
    }

    jobManager.monitorReport(
      id,
      rand.nextInt(3), // upper bound exclusive, so max 2 slots
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

  var availableTaskSlots = 2
  val taskSlots = scala.collection.mutable.Map[String, TaskSlot]()

  // ServerSocket used for INCOMING data
  val port = 9000 + id
  val serverSocket = new ServerSocket(port)
  myPrint("Server socket started on port: " + port)

  new Thread {
    override def run {
      // wait for incoming socket connection and read the corresponding jobID
      while (true) {
        val inputSocket = serverSocket.accept()
        val inputStream = new DataInputStream(inputSocket.getInputStream())
        val jobID = inputStream.readInt() // jobID communicated through socket
        val taskID = inputStream.readInt() // taskID communicated through socket
        myPrint(
          "Connected inputsocket for (jobID, taskID): (" + jobID + ", " + taskID + ")"
        )
        var taskSlot = getTaskSlot(jobID, taskID)
        // assign output socket and operator type
        taskSlot.from :+ new DataInputStream(inputSocket.getInputStream())

        // try to run the task slot
        runTaskSlot(jobID, taskID)
      }
    }
  }.start()

  def getTaskSlot(jobID: Int, taskID: Int): TaskSlot = {
    taskSlots.synchronized { // prevent creating a taskSlot twice
      val key = jobID + "" + taskID
      // try to find already existing one
      var taskSlot = taskSlots.getOrElse(key, null)

      // create new one
      if (taskSlot == null) {
        myPrint("Creating new taskslot for key " + key)
        taskSlot = new TaskSlot(key)
        taskSlots.put(key, taskSlot)
      }
      return taskSlot
    }
  }

  def assignTask(task: Task): Unit = {
    myPrint("Received task with jobID: " + task.jobID)
    var taskSlot = getTaskSlot(task.jobID, task.taskID)

    taskSlot.operator = task.operator
    taskSlot.fromCount = task.from.length

    // Set output streams (if this is not the sink)
    if (task.to.length > 0) {
      var taskIDCopy = task.taskID

      for (i <- task.to.indices) {
        taskIDCopy += 1
        val outputSocket = new Socket("localhost", 8000 + task.to(i))
        val dos = new DataOutputStream(outputSocket.getOutputStream())
        dos.writeInt(
          task.jobID
        ) // let receiver know the jobID corresponding to this socket
        dos.writeInt(
          taskIDCopy
        ) // taskID should correspond to what is sent via the assignTask() call. So the order of to: [] should correspond with the order of assigning tasks in the JobManager
        taskSlot.to :+ dos
      }
    }

    // try to run the task slot
    runTaskSlot(task.jobID, task.taskID)
  }

  def runTaskSlot(jobID: Int, taskID: Int) = {
    val slot: TaskSlot = getTaskSlot(jobID, taskID)

    // if map or reduce
    // (outputsocket can be null in case of sink)
    if (
      slot.from.length == slot.fromCount &&
      slot.operator != null
    ) {
      new Thread(slot).start()
    }
    // if data source (no inputsocket needed)
    else if (
      slot.to.length > 0 &&
      slot.operator != null &&
      slot.operator.equals("data")
    ) {
      new Thread(slot).start()
    }
  }

  def myPrint(text: String) {
    println(id + ": " + text)
  }
}

case class TaskManagerInfo(
    id: Int,
    var numSlots: Int,
    var numTasksDeployed: Int,
    var latenciesToSelf: Array[Latency],
    var bandwidthsToSelf: Array[BW],
    var ipRate: Int,
    var opRate: Int
)
