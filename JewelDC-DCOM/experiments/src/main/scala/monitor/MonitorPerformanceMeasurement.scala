package monitor

import java.rmi.registry.LocateRegistry

object MonitorPerformanceMeasurement {

  def main(args: Array[String]): Unit = {

    val registry = LocateRegistry.getRegistry()
    val remoteEvaluator = new RemoteEvaluator(registry)
    val monitor = new Monitor(remoteEvaluator)

    for (i <- 1 to 100) {
      val expressionSize = i * 100
      for (_ <- 1 to 20) {
        val exp = ExpressionGenerator.generateRandomExpression(expressionSize)
        val startTime = System.currentTimeMillis()
        val (_,_,endTime) = monitor.monitorAndEvaluate(exp)
        val timeCalculating = endTime - startTime
        val timeMonitoring = System.currentTimeMillis() - startTime
        val startTimeNoMonitoring = System.currentTimeMillis()
        val _ = remoteEvaluator.evaluateExpressionRemotelyUnmonitored(exp)
        val TimeNoMonitoring = System.currentTimeMillis() - startTimeNoMonitoring
        println(expressionSize + "," + TimeNoMonitoring + "," + timeCalculating + "," + timeMonitoring)
      }
    }

  }
}
