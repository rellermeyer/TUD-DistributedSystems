package nl.tudelft.IN4391G4.machines

import nl.tudelft.IN4391G4.extensions.ConfigExtensions
import java.util
import java.util.Iterator

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory, ConfigValue}

class Pool() {

  private val config: Config = ConfigFactory.load()
  private val machines: util.List[Machine] = new util.ArrayList[Machine]
  private val system: ActorSystem = initializeSystem()

  initialiseMachines()

  //invokes start on each initialised machine
  def takeOff(): Unit = {
    machines.forEach(m => m.start())
  }

  private def initializeSystem(): ActorSystem = {
    val poolName = ConfigExtensions.getStringSafe(config, "akka.poolName", "defaultpoolname")
    ActorSystem(poolName)
  }

  private def initialiseMachines(): Unit = {
    val machineIterator: Iterator[ConfigValue] = config.getList("machines").iterator()
    while(machineIterator.hasNext) {
      val machineConf: Config = machineIterator.next.atKey("_")

      val machineType = ConfigExtensions.getStringSafe(machineConf, "_.type", "typefallbackvalue")
      machineType match {
        case "CentralManager" => machines.add(new CentralManager(system, machineConf))
        case "Gateway" => machines.add(new Gateway(system, machineConf))
        case "Workstation" => machines.add(new Workstation(system, machineConf))
        case _ => {
          Console.err.println(s"Unknown machine type '$machineType'. Supported types are 'CentralManager', 'Gateway' and 'Workstation'.")
          System.exit(1)
        }
      }
    }
  }
}