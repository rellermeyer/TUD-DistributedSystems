package jobmanager

import java.rmi.registry.LocateRegistry
import java.rmi.Naming
import taskmanager._
import executionplan._

object JobManager {
  val registryPort = 2000
  val registryURL = "rmi://localhost:" + registryPort + "/taskmanager"
  val registry = LocateRegistry.createRegistry(registryPort)

  // Initialize TaskManagers
  val no_task_managers = 3;
  val taskManagers = new Array[TaskManager](no_task_managers)

  for (i <- taskManagers.indices) {
    taskManagers(i) = new TaskManager(i, registryURL)
  }

  // test
  val remoteNode: TaskManagerInterface = Naming.lookup(registryURL + "0").asInstanceOf[TaskManagerInterface]
  
  // TODO: Actual Task
  remoteNode.assignTask(new Task("node0", "node1", "map", (x: Int) => x + 1))
}
