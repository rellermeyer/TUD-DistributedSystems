package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import java.rmi.server.UnicastRemoteObject
import executionplan._
import scala.collection.mutable.ArrayBuffer

object JobManager extends UnicastRemoteObject with JobManagerInterface {
  /* 
  *  Start a Job Manager that creates a registry
  *  at a specific port for Task Manager JVM
  *  instances to connect to
  */
  val taskManagers = ArrayBuffer[TaskManagerInfo]()
  def main(args: Array[String]) = {
    val registryPort = 1099
    val registryURL = "rmi://localhost:" + registryPort + "/taskmanager"
    val registry = LocateRegistry.createRegistry(registryPort)

    // Initialize TaskManagers

  }

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
