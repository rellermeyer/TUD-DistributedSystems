import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

object Registry extends UnicastRemoteObject {
  def main(args: Array[String]): Unit = {
    /*
     *  Create global registry for the JobManager and TaskManagers to connect to.
     */
    val registryPort = 1098
    val registry = LocateRegistry.createRegistry(registryPort)
    println("Registry running on port " + registryPort)
  }
}
