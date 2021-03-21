package taskmanager

import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import jobmanager._

object ILPTester {

  def main(args: Array[String]): Unit = {
    val taskManagers = ArrayBuffer[TaskManagerInfo]()
    val reconfigurationManager = ReconfigurationManager

    // init taskmanagers
    val rand = new Random

    val no_taskManagers = 3 // exclude JobManager

    // Simulate some random bandwidth and latency
    for (i <- 0 until no_taskManagers) {
      var latencies = new Array[Latency](no_taskManagers)
      var bws = new Array[BW](no_taskManagers)
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
        latencies(j) = new Latency(
          j,
          Random.nextFloat() * 3 // max 3 seconds latency
        )  
        if (i != j) {                                              // TODO: check minimum, maximum value of bws (currently 500 - 3000)
          bws(j) = new BW(j, 500 + Random.nextFloat() * 3000)     // Be careful, bandwidth should be large enough or ILP might be infeasible
        } else {
          bws(j) = new BW(j, 0)
        }
      }

      taskManagers(i).numSlots = rand.nextInt(10) // Be careful, if every site gets a small num of slots, ILP might be infeasible
      taskManagers(i).latenciesToSelf = latencies
      taskManagers(i).bandwidthsToSelf = bws
      taskManagers(i).ipRate = rand.nextInt(1000)
      taskManagers(i).opRate = rand.nextInt(500)
      println(taskManagers(i).numSlots)

    }

    for (i <- taskManagers.indices) {
      println(taskManagers(i).bandwidthsToSelf.mkString(", "))
    }

    reconfigurationManager.solveILP(taskManagers, 5.0.toFloat)
  }
}
