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

    val no_taskManagers = registry.list().length - 1 // exclude JobManager

    var latencies = new Array[Latency](no_taskManagers)
    var bws = new Array[BW](no_taskManagers)

    // Simulate some random bandwidth and latency
    for (i <- 0 until no_taskManagers) {
      latencies(i) = new Latency(
        i,
        Random.nextFloat()*3 // max 3 seconds latency
        )
      if (i != id) {
        bws(i) = new BW(i, Random.nextFloat()*3)    
      }
    }

    // jobManager.monitorReport(
    //   id,
    //   rand.nextInt(3), // upper bound exclusive, so max 2 slots
    //   latencies, 
    //   bws, 
    //   rand.nextInt(1000),
    //   rand.nextInt(500)
    // )
  }
}


class TaskManager(val id: Int)
    extends UnicastRemoteObject
    with TaskManagerInterface {

  var availableTaskSlots = 2
  val taskSlots = scala.collection.mutable.Map[Int, TaskSlot]()

  // ServerSocket used for INCOMING data
  val port = 8000 + id
  val serverSocket = new ServerSocket(port)
  myPrint ("Server socket started on port: " + port)

  new Thread {
    override def run {
      // wait for incoming socket connection and read the corresponding jobID
      while (true) {
        val inputSocket = serverSocket.accept()
        val inputStream = new DataInputStream(inputSocket.getInputStream())
        val jobID = inputStream.readInt() // jobID communicated through socket
        myPrint("Connected inputsocket for jobID: " + jobID)
        
        taskSlots.synchronized { // prevent accessing taskSlots at same time as assignTask()
          var taskSlot = getTaskSlot(jobID)
          // assign output socket and operator type
          taskSlot.inputSocket = inputSocket

          // try to run the task slot
          runTaskSlot(jobID)
        }
      }
    }
  }.start()

  def getTaskSlot(jobID: Int): TaskSlot = {
    // try to find already existing one
    var taskSlot = taskSlots.getOrElse(jobID, null)

    // create new one
    if (taskSlot == null) {
      myPrint("Creating new taskslot for jobID " + jobID)
      taskSlot = new TaskSlot(jobID)
      taskSlots.put(jobID, taskSlot)
    }
    return taskSlot
  }

  def assignTask(task: Task): Unit = {
    taskSlots.synchronized { // prevent accessing taskSlots at same time as serversocket
      myPrint("Received task with jobID: " + task.jobID)
      var taskSlot = getTaskSlot(task.jobID)
      
      if (task.to != -1) { // if this is not the sink, propagate output to next taskslot
        val outputSocket = new Socket("localhost", 8000 + task.to)
        new DataOutputStream(outputSocket.getOutputStream()).writeInt(task.jobID) // let receiver know the jobID corresponding to this socket
        taskSlot.outputSocket = outputSocket
      }
      taskSlot.operator = task.operator

      // try to run the task slot
      runTaskSlot(task.jobID)
    }
  }

  def runTaskSlot(jobID: Int) = {
    val slot: TaskSlot = taskSlots.get(jobID).get

    // if map or reduce
    // (outputsocket can be null in case of sink)
    if (slot.inputSocket != null && slot.operator != null) { 
      new Thread(slot).start()
    }
    // if data source (no inputsocket needed)
    else if (slot.outputSocket != null && slot.operator != null && slot.operator.equals("data")) { 
      new Thread(slot).start()
    }
  }

  def myPrint(text: String) {
    println (id + ": " + text)
  }
}
