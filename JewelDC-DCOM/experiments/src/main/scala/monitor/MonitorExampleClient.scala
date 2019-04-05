package monitor

import java.rmi.registry.LocateRegistry

import common._

/**
  * An RMI client that creates an arithmetic expression and displays its event logs.
  */
object MonitorExampleClient {

  def main(args: Array[String]): Unit = {
    val registry = LocateRegistry.getRegistry()
    val remoteEvaluator = new RemoteEvaluator(registry)
    val monitor = new Monitor(remoteEvaluator)


    // (10 - 5) + (3 * 6)
    val complexExpression =
      BinaryExpression(PlusOp(),
        BinaryExpression(MinusOp(), NumberLiteral(10), NumberLiteral(5)),
        BinaryExpression(MultOp(), NumberLiteral(3), NumberLiteral(6))
      )

    println("Computing " + complexExpression)

    val (result, eventLog, _) = monitor.monitorAndEvaluate(complexExpression)

    println()
    println("Result is \n" + result)
    println()
    println("Event Log is \n" + eventLog.mkString("\n"))
  }

}
