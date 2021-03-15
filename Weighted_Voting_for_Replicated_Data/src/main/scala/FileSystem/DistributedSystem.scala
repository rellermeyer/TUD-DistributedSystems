package FileSystem

import scala.util.Random

class DistributedSystem(numContainers: Int, latencies: Seq[Int], newFailProb: Double) {

  /**
   * constructor
   */
  private val _containers: Seq[Container] = createContainers(numContainers, latencies )
  private val _failProb: Double = newFailProb

  /**
   * Initialize a number of new containers
   * New containers can be added to existing set
   * @param latencies
   */

  def createContainers(numContainers: Int, latencies: Seq[Int]): Seq[Container] = {
    var newContainers: Seq[Container] = Seq.empty[Container]
    for (l <- latencies) {
      newContainers = newContainers :+ Container(l)
      println("Created container with latency " + l)
    }
    newContainers
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
  //TODO: error message if suite doesn't exist
  def collectSuite(suiteId: Int): FileSystemResponse = {
    val response: FileSystemResponse = FileSystemResponse()
    var rep: Option[Representative] = None
    val r: Random = scala.util.Random
    var event: Double = r.nextDouble()
      for (cid <- _containers.indices) {
        rep = _containers(cid).findRepresentative(suiteId)
        if (rep.isDefined && event >= _failProb) {
          response.addResponse(ContainerResponse(cid, _containers(cid).latency, rep.get.weight, rep.get.prefix))
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
  def apply(numContainers: Int, latencies: Seq[Int], newFailProb: Double): DistributedSystem = {
    val newSystem = new DistributedSystem(numContainers, latencies, newFailProb)
    newSystem
  }
}
