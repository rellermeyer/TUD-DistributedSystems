// package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import executionplan._
import scala.collection.mutable.ArrayBuffer
import taskmanager._

object JobManagerRunner {
  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry(1099)
    val JobManager = new JobManager
    registry.bind("jobmanager", JobManager)
    println("JobManager bound!")
  }
}

class JobManager extends UnicastRemoteObject with JobManagerInterface {
  val taskManagers = ArrayBuffer[TaskManagerInfo]()

  // register the poor taskmanager
  def register(): Int = {
    val tmsLength = taskManagers.length
    taskManagers.append(new TaskManagerInfo(tmsLength, 0))
    return tmsLength;
  }

  // update metrics about a taskmanager
  def monitorReport(id: Int, bandwidth: Int) = {
    taskManagers(id).bandwidth = bandwidth
  }
}

case class TaskManagerInfo(id: Int, var bandwidth: Int)
