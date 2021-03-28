package FileSystem

import scala.util.Random

class DistributedSystem(numContainers: Int, latencies: Seq[Int], newFailProb: Double) {

  /**
   * Constructor
   */
  case class FailResult(reason:String)

  private val _containers: Either[FailResult, Seq[Container]] = createContainers(numContainers, latencies)
  private val _failProb: Double = newFailProb

  /**
   * Initialize a number of new containers
   * New containers can be added to existing set
   * @param numContainers
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
  def createRepresentatives(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
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
  def collectRepresentatives(suiteId: Int): Either[FailResult, FileSystemResponse] = {
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


  /**
   * Reading the content of the representative
   * @param containerId
   * @param suiteId
   * @return content
   */
  def readRepresentative(containerId: Int, suiteId: Int): Either[FailResult, (Int, Int)] = {
    _containers match {
      case Left(f) => Left(FailResult("readSuite failed:\n" + f.reason))
      case Right(c) => {
        if (containerId >= 0 && containerId < c.length) {
          val rep = c(containerId).findRepresentative(suiteId)
          if (rep.isDefined) {
            Right(rep.get.content, c(containerId).latency)
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

  /**
   * Writing content to the representative
   * @param containerIds
   * @param suiteId
   * @param newContent
   * @return
   */

  def writeRepresentatives(containerIds: Seq[Int], suiteId: Int, newContent: Int): Either[FailResult, Int] = {
    _containers match {
      case Left(f) => Left(FailResult("writeSuite failed:\n" + f.reason))
      case Right(c) => {
        var latency: Int = 0
        for (cid <- containerIds) {
          var result = c(cid).writeRepresentative(suiteId, newContent)
          result match {
            case Left(f) => return Left(FailResult("writeSuite failed:\n" + f.reason))
            case Right(r) => {
              if (c(cid).latency > latency) {
                latency = c(cid).latency
              }
            }
          }
        }
        Right(latency)
      }
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
