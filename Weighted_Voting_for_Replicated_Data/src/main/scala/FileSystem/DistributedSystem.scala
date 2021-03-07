package FileSystem

class DistributedSystem {

  /**
   * constructor
   */
  private var _containers: Vector[Container] = scala.collection.immutable.Vector.empty

  def createContainers(numContainers: Int, latencies: Seq[Int]): Unit = {
    if (numContainers == latencies.length) {
      val newIndices = _containers.length to (_containers.length + numContainers)
      for ((l, i) <- latencies.zip(newIndices)) {
        _containers = _containers :+ Container(i)
        println("Created container " + i + " with latency " + l)
      }
    }
    else {
      println("Error: number of latencies does not match number of new containers")
    }
  }

  def createSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Unit = {
    if (repWeights == _containers.length) {
      for ((c, w) <- _containers.zip(repWeights)) {
        c.createRepresentative(suiteId, suiteR, suiteW, w)
      }
    }
    else {
      println("Error: number of weights does not match number of containers")
    }
  }

  /*def readSuite(suiteId: Int): Int = {
    //for (element <- _containers) {
     // println(element. //TODO
    //}
  }

  def createSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int): Unit = {
    for (element <- _containers) {
      element.createRepresentative(suiteId, suiteR, suiteW, repWeight) //TODO
    }
  }*/
}

/**
 * companion object
 */
object DistributedSystem {
  def apply(): DistributedSystem = {
    val newSystem = new DistributedSystem()
    newSystem
  }
}
