package subtractor

import java.rmi.registry.LocateRegistry
import java.rmi.server.UnicastRemoteObject

import common.Evaluator

/**
  * An RMI client that registers an evaluator to the rmiregistry that can subtracts two expressions. The object on the
  * registry can be identified under the name "subtractor.Subtractor".
  */
object SubtractorClient {

  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry()
    val subtractor = new Subtractor(registry)
    val stub = UnicastRemoteObject.exportObject(subtractor, 0).asInstanceOf[Evaluator]
    registry.bind("Subtractor", stub)

    println("Subtractor ready")
  }

}
