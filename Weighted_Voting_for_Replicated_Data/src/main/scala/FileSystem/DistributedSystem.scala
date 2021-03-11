package FileSystem

import scala.util.Random

class DistributedSystem(newFailProb: Double) {

  /**
   * constructor
   */
  private var _containers: Seq[Container] = Seq.empty[Container]
  private var _failProb: Double = newFailProb

  /**
   * Initialize a number of new containers
   * New containers can be added to existing set
   * @param numContainers
   * @param latencies
   */

  def createContainers(latencies: Seq[Int]): Unit = {
    for (l <- latencies) {
      _containers = _containers :+ Container(l)
      println("Created container with latency " + l)
    }
  }

  /*def createContainers(numContainers: Int, latencies: Seq[Int]): Unit = {
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
  }*/

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
      for (cid <- _containers.indices) {
        _containers(cid).createRepresentative(suiteId, suiteR, suiteW, repWeights(cid))
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
  //TODO: Struct for response
  //TODO: error message if suite doesn't exist
  def collectSuite(suiteId: Int): Seq[(Int, Int, Int, Int)] = {
    var response: Seq[(Int, Int, Int, Int)] = Seq.empty[(Int, Int, Int, Int)]
    var rep: Option[Representative] = None
    val r: Random = scala.util.Random
    var event: Double = r.nextDouble()
      for (cid <- _containers.indices) {
        rep = _containers(cid).findRepresentative(suiteId)
        if (rep.isDefined && event >= _failProb) {
          response = response :+ (cid, _containers(cid).latency, rep.get.weight, rep.get.prefix.versionNumber)
        }
        event = r.nextFloat()
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

  def writeSuite(containerIds: Seq[Int], suiteId: Int, newContent: Int): Unit = {
    for (cid <- containerIds) {
      _containers(cid).writeRepresentative(suiteId, newContent)
    }
  }
}

/**
 * companion object
 */
object DistributedSystem {
  def apply(newFailProb: Double): DistributedSystem = {
    val newSystem = new DistributedSystem(newFailProb)
    newSystem
  }
}
