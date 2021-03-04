import java.rmi.registry.LocateRegistry
import java.rmi.Naming
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

  val remoteNode: TaskManagerInterface = Naming.lookup(registryURL + "0").asInstanceOf[TaskManagerInterface]
  remoteNode.assignTask()
}
