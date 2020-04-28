package org.orleans.silo.metrics

import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.GrainInfo
import org.orleans.silo.control.MasterGrain

class LoadMonitor(val grainMap: ConcurrentHashMap[String, List[GrainInfo]], val masterGrain: MasterGrain)
  extends Runnable with LazyLogging{

  var running: Boolean = true
  val FREQUENCY: Int = 1000
  // Final value to figure out
  val REPLICATION_TRESHOLD = 100

  override def run(): Unit = {
    logger.warn("Started load monitor on master.")
    var oldTime: Long = System.currentTimeMillis()

    while (running) {
      val newTime: Long = System.currentTimeMillis()
      val timeDiff = newTime - oldTime

      // Check if it is time to send heartbeats again.
      if (timeDiff >= FREQUENCY) {
        oldTime = newTime
        grainMap.forEach((id, grainList) => {
          val avgLoad: Double = grainList.foldLeft(0.0)((acc, b) => acc + b.load) / grainList.length
          if (avgLoad > REPLICATION_TRESHOLD) {
            masterGrain.triggerGrainReplication(id)
          }
        }
        )
      }
    }
  }

}
