package multiplier

import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

import common.Evaluator

/**
  * An RMI client that registers an evaluator to the rmiregistry that can multiply two expressions. The object on the
  * registry can be identified under the name "multiplier.Multiplier".
  */
object MultiplierClient {

  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry()
    val multiplier = new Multiplier(registry)
    val stub = UnicastRemoteObject.exportObject(multiplier, 0).asInstanceOf[Evaluator]
    registry.bind("Multiplier", stub)

    println("Multiplier ready")
  }

}
