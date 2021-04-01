package FileSystem

class FileSystem(numContainers: Int, latencies: Seq[Int], newBlockingProbs: Seq[Double]) {

  /**
   * Constructor
   */



  case class FailResult(reason:String)

  private var _containers: Either[FailResult, Seq[Container]] = Left(FailResult("no containers have been instantiated"))

  /**
   * Initialize a number of new containers
   * New containers can be added to existing set
   * @param numContainers
   * @param latencies
   */

  def initTentativeSystem(): Either[FailResult, Unit] = {
    _containers match {
      case Left(f) => Left(FailResult("setTentativeSystemState():\n" + f.reason))
      case Right(containers) => {
        for (c <- containers) {
          c.initTentativeContainer()
        }
        Right()
      }
    }
  }

  def commitTentativeSystem(): Either[FailResult, Unit] = {
    _containers match {
      case Left(f) => Left(FailResult("setTentativeSystemState():\n" + f.reason))
      case Right(containers) => {
        for (c <- containers) {
          c.commitTentativeContainer()
        }
        Right()
      }
    }
  }

  def createContainers(numContainers: Int, latencies: Seq[Int], blockingProbs: Seq[Double]): Either[FailResult, Unit] = {
    if (numContainers != latencies.length || numContainers != blockingProbs.length) {
      Left(FailResult("createContainers failed: number of latencies or blocking probabilities does not match number of containers"))
    }
    else {
      var newContainers: Seq[Container] = Seq.empty[Container]
      for (l <- latencies zip blockingProbs) {
        newContainers = newContainers :+ Container(l._1, l._2)
      }
      _containers = Right(newContainers)
      Right()
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
            val result = c(cid).createRepresentative(suiteId, suiteR, suiteW, repWeights, repWeights(cid))
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
  def collectRepresentatives(suiteId: Int): Either[FailResult, (FileSystemResponse, Int)] = {
    var latency: Int = 0

    _containers match {
      case Left(f) => Left(FailResult("collectSuite failed:\n" + f.reason))
      case Right(c) => {

        val response: FileSystemResponse = FileSystemResponse()
        var rep: Option[Representative] = None

        for (cid <- c.indices) {
          rep = c(cid).findRepresentative(suiteId)
          if (rep.isDefined) {
            response.addResponse(ContainerResponse(cid, c(cid).latency, rep.get.weight, rep.get.prefix))
            if (c(cid).latency > latency) {
              latency = c(cid).latency
            }
          }
          else {
            return Left(FailResult("collectSuite failed: representative of file " + suiteId +
              " could not be found in container " + cid))
          }
        }
        if (response.containerResponses.nonEmpty) {
          Right(response, latency)
        }
        else {
          Left(FailResult("collectSuite failed: no representatives could be found"))
        }
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
      case Left(f) => Left(FailResult("readRepresentative failed:\n" + f.reason))
      case Right(c) => {

        if (containerId >= 0 && containerId < c.length) {
          val result = c(containerId).readRepresentative(suiteId)
          result match {

            case Left(f) => Left(FailResult("readRepresentatives failed:\n" + f.reason))
            case Right(result) => Right(result._1, result._2)
          }
        }
        else {
          Left(FailResult("readRepresentative failed: container with id " + containerId + " does not exist"))
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

  def writeRepresentatives(containerIds: Seq[Int], suiteId: Int, newContent: Int, increment: Boolean): Either[FailResult, Int] = {
    var latency: Int = 0

    _containers match {
      case Left(f) => Left(FailResult("writeRepresentatives failed:\n" + f.reason))
      case Right(c) => {

        for (cid <- containerIds) {
          val result = c(cid).writeRepresentative(suiteId, newContent, increment)
          result match {
            case Left(f) => return Left(FailResult("writeRepresentatives failed:\n" + f.reason))
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
object FileSystem {
  def apply(numContainers: Int, latencies: Seq[Int], newFailProbs: Seq[Double]): FileSystem = {
    val newSystem = new FileSystem(numContainers, latencies, newFailProbs)
    newSystem.createContainers(numContainers, latencies, newFailProbs)
    newSystem
  }
}
