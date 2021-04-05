package adder

import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

import common.Evaluator

/**
  * An RMI client that registers an evaluator to the rmiregistry that can sum two expressions. The object on the
  * registry can be identified under the name "Adder".
  */
object AdderClient {

  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry()
    val adder = new Adder(registry)
    val stub = UnicastRemoteObject.exportObject(adder, 0).asInstanceOf[Evaluator]
    registry.bind("Adder", stub)

    println("Adder ready")
  }

}
