import scala.collection.mutable.ArrayBuffer
import scala.util.Random
// package taskmanager

object ILPTester {
  def main(args: Array[String]): Unit = {
    val taskManagers = ArrayBuffer[TaskManagerInfo]()
    val reconfigurationManager = ReconfigurationManager
    val alpha = 0.8.toFloat

    // init taskmanagers
    val rand = new Random

    val no_taskManagers = 3 // exclude JobManager

    var latencies = new Array[Latency](no_taskManagers)
    var bws = new Array[BW](no_taskManagers)

    // Simulate some random bandwidth and latency
    for (i <- 0 until no_taskManagers) {
      taskManagers.append(
        new TaskManagerInfo(
          id = i,
          0,
          0,
          Array.empty[Latency],
          Array.empty[BW],
          0,
          0
        )
      )
      for (j <- 0 until no_taskManagers) {
        latencies(i) = new Latency(
          i,
          Random.nextFloat() * 3 // max 3 seconds latency
        )
        if (i != j) {
          bws(i) = new BW(i, Random.nextFloat() * 3)
        } else {
          bws(i) = new BW(i, 0)
        }

        taskManagers(i).numSlots = rand.nextInt(3)
        taskManagers(i).latenciesToSelf = latencies
        taskManagers(i).bandwidthsToSelf = bws
        taskManagers(i).ipRate = rand.nextInt(1000)
        taskManagers(i).opRate = rand.nextInt(500)
      }
    }

    for (i <- taskManagers.indices) {
      println(taskManagers(i).bandwidthsToSelf.mkString(", "))
    }
    reconfigurationManager.solveILP(taskManagers, 1.0.toFloat, alpha)
  }
}
