package FileSystem

import scala.util.Random

class DistributedSystem(numContainers: Int, latencies: Seq[Int], newFailProb: Double) {

  /**
   * constructor
   */
  case class FailResult(reason:String)

  private val _containers: Either[FailResult, Seq[Container]] = createContainers(numContainers, latencies)
  private val _failProb: Double = newFailProb

  /**
   * Initialize a number of new containers
   * New containers can be added to existing set
   * @param latencies
   */

  def createContainers(numContainers: Int, latencies: Seq[Int]): Either[FailResult, Seq[Container]] = {
    if (numContainers != latencies.length) {
      Left(FailResult("createContainers failed: number of latencies does not match number of containers"))
    }
    else {
      var newContainers: Seq[Container] = Seq.empty[Container]
      for (l <- latencies) {
        newContainers = newContainers :+ Container(l)
        println("Created container with latency " + l)
      }
      Right(newContainers)
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
  def createSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
    _containers match {
      case Left(f) => Left(FailResult("createSuite failed:\n" + f.reason))
      case Right(c) => {
        if (repWeights.length != c.length) {
          Left(FailResult("createSuite failed: number of weights (" + repWeights.length +
            ") does not match number of containers (" + c.length + ")"))
        }
        else {
          for (cid <- c.indices) {
            var result = c(cid).createRepresentative(suiteId, suiteR, suiteW, repWeights(cid))
            result match {
              case Left(f) => return Left(FailResult("createSuite failed:\n" + f.reason))
              case Right(r) => {}
            }
          }
          Right()
        }
      }
    }
  }

  /**
   * Poll all containers for a quorum, gather corresponding latencies and weights
   * @param suiteId
   * @return
   */
  //TODO: do containers always respond, or only when they have a representative?
  //TODO: error message if suite doesn't exist
  def collectSuite(suiteId: Int): Either[FailResult, FileSystemResponse] = {
    _containers match {
      case Left(f) => Left(FailResult("collectSuite failed:\n" + f.reason))
      case Right(c) => {
        val response: FileSystemResponse = FileSystemResponse()
        var rep: Option[Representative] = None
        val r: Random = scala.util.Random
        var event: Double = r.nextDouble()
        for (cid <- c.indices) {
          rep = c(cid).findRepresentative(suiteId)
          if (rep.isDefined) {
            if (event >= _failProb) {
              response.addResponse(ContainerResponse(cid, c(cid).latency, rep.get.weight, rep.get.prefix))
            }
          }
          else {
            return Left(FailResult("collectSuite failed: representative of file " + suiteId +
              " could not be found in container " + cid))
          }
          event = r.nextFloat()
        }
        Right(response)
      }
    }
  }

  def readSuite(containerId: Int, suiteId: Int): Either[FailResult, Int] = {
    _containers match {
      case Left(f) => Left(FailResult("readSuite failed:\n" + f.reason))
      case Right(c) => {
        if (containerId >= 0 && containerId < c.length) {
          val rep = c(containerId).findRepresentative(suiteId)
          if (rep.isDefined) {
            Right(rep.get.content)
          }
          else {
            Left(FailResult("readSuite failed: representative of file " + suiteId +
              " could not be found in container " + containerId))
          }
        }
        else {
          Left(FailResult("readSuite failed: container with id " + containerId + " does not exist"))
        }
      }
    }
  }

  def writeSuite(containerIds: Seq[Int], suiteId: Int, newContent: Int): Either[FailResult, Unit] = {
    _containers match {
      case Left(f) => Left(FailResult("writeSuite failed:\n" + f.reason))
      case Right(c) => {
        for (cid <- containerIds) {
          var result = c(cid).writeRepresentative(suiteId, newContent)
          result match {
            case Left(f) => return Left(FailResult("writeSuite failed:\n" + f.reason))
            case Right(r) => {}
          }
        }
      }
    }
    Right()
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
