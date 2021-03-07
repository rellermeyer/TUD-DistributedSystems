package FileSystem

class DistributedSystem {

  /**
   * constructor
   */
  private var _containers: Seq[Container] = Seq.empty[Container]

  /**
   * Initialize a number of new containers
   * New containers can be added to existing set
   * @param numContainers
   * @param latencies
   */

  /*def createContainers(latencies: Seq[Int]): Unit = {
    for (l <- latencies) {
      _containers = _containers :+ Container(l)
      println("Created container with latency " + l)
    }
  }*/

  def createContainers(numContainers: Int, latencies: Seq[Int]): Unit = {
    if (numContainers == latencies.length) {
      val newIndices = _containers.length to (_containers.length + numContainers)
      for ((l, i) <- latencies.zip(newIndices)) {
        _containers = _containers :+ Container(i, l)
        println("Created container " + i + " with latency " + l)
      }
    }
    else {
      println("Error: number of latencies does not match number of new containers")
    }
  }

  /**
   * Initialize a new file suite on all existing containers
   * representative weights are assigned in order in which they are passed
   * @param suiteId
   * @param suiteR
   * @param suiteW
   * @param repWeights
   */
  def createSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Unit = {
    if (repWeights.length == _containers.length) {
      for ((c, w) <- _containers.zip(repWeights)) {
        c.createRepresentative(suiteId, suiteR, suiteW, w)
      }
    }
    else {
      println("Error: number of weights does not match number of containers")
    }
  }

  /**
   * Poll all containers for a quorum, gather corresponding latencies and weights
   * @param suiteId
   * @return
   */
  //TODO: do containers always respond, or only when they have a representative?
  def collectSuite(suiteId: Int): Seq[(Int, Int, Int)] = {
    var response: Seq[(Int, Int, Int)] = Seq.empty[(Int, Int, Int)]
    var rep: Option[Representative] = None
      for (c <- _containers) {
        rep = c.findRepresentative(suiteId)
        if (rep.isDefined) {
          response = response :+ (c.containerId, c.latency, rep.get.weight)
        }
      }
    response
  }

  def readSuite(containerId: Int, suiteId: Int): Int = {
    if (containerId >= 0 && containerId < _containers.length) {
      var rep = _containers(containerId).findRepresentative(suiteId)
      if (rep.isDefined) {
        rep.get.content
      }
      else {
        -1
      }
    }
    else {
      -1 //TODO: better error handling
    }
  }
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
