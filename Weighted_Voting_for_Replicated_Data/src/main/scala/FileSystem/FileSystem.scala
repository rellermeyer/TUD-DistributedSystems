package FileSystem

class FileSystem(/*numContainers: Int, latencies: Seq[Int], newBlockingProbs: Seq[Double]*/) {

  /**
   * Case class for handling failed method calls
   * @param reason a textual explanation of the reason for failure
   */
  case class FailResult(reason:String)

  /**
   * Private class field
   */
  private var _containers: Either[FailResult, Seq[Container]] = Left(FailResult("no containers have been instantiated"))

  /**
   * Set tentative state for each container at the start of a new transaction
   * @return either failure or nothing
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

  /**
   * Set definitive state for each container when committing a transaction
   * @return either failure or nothing
   */
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

  /**
   * Initialize a number of new containers. New containers can be added to existing set
   * @param numContainers the number of new containers
   * @param latencies the latencies of the new containers
   * @param blockingProbs the blocking probabilities of the new containers
   * @return either failure or nothing
   */
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
   * Initialize a new file suite representative on each container.
   * Representative weights are assigned in order in which they are passed
   * @param suiteId Id of the new file suite
   * @param suiteR r value of the new file suite
   * @param suiteW w value of the new file suite
   * @param repWeights voting weights for each new rep.
   * @return either failure or nothing
   */
  def createRepresentatives(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
    _containers match {
      case Left(f) => Left(FailResult("createRepresentatives failed:\n" + f.reason))
      case Right(containers) => {
        if (repWeights.length != containers.length) {
          Left(FailResult("createRepresentatives failed: number of weights (" + repWeights.length +
            ") does not match number of containers (" + containers.length + ")"))
        }
        else {
          for (cid <- containers.indices) {
            val result = containers(cid).createRepresentative(suiteId, suiteR, suiteW, repWeights, repWeights(cid))
            result match {
              case Left(f) => return Left(FailResult("createRepresentatives failed:\n" + f.reason))
              case Right(r) => {}
            }
          }
          Right()
        }
      }
    }
  }

  /**
   * Delete the reps. belonging to a file suite in each container
   * @param suiteId ID of the file suite that is to be deleted
   * @return either failure or nothing
   */
  def deleteRepresentatives(suiteId: Int): Either[FailResult, Unit] = {
    _containers match {
      case Left(f) => Left(FailResult("deleteRepresentatives failed:\n" + f.reason))
      case Right(containers) => {
        for (container <- containers) {
          val result = container.deleteRepresentative(suiteId)
          result match {
            case Left(f) => return Left(FailResult("deleteRepresentatives failed:\n" + f.reason))
            case Right(r) => {}
          }
        }
        Right()
      }
    }
  }

  /**
   * Request representatives belonging to a specific file suite from each container
   * A container may refrain from responding if it does not hold the rep., or if it blocks
   * @param suiteId ID of the requested file suite
   * @return either failure of a series of responses from containers that hold a corresponding representative
   */
  def collectRepresentatives(suiteId: Int): Either[FailResult, (Seq[ContainerResponse], Int)] = {
    var latency: Int = 0

    _containers match {
      case Left(f) => Left(FailResult("collectSuite failed:\n" + f.reason))
      case Right(c) => {

        var responses: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
        var rep: Option[Representative] = None

        for (cid <- c.indices) {
          rep = c(cid).findRepresentative(suiteId)
          if (rep.isDefined) {
            responses = responses :+ ContainerResponse(cid, c(cid).latency, rep.get.weight, rep.get.prefix)
            if (c(cid).latency > latency) {
              latency = c(cid).latency
            }
          }
        }
        if (responses.nonEmpty) {
          Right(responses, latency)
        }
        else {
          Left(FailResult("collectSuite failed: no representatives could be found"))
        }
      }
    }
  }


  /**
   * Read the content of a representative held by a specific container
   * @param containerId ID of the addressed container
   * @param suiteId ID of the rep. to be read
   * @return either failure or a tuple containing the content and latency for the addressed container
   */
  def readRepresentative(containerId: Int, suiteId: Int): Either[FailResult, (Int, Int)] = {
    _containers match {
      case Left(f) => Left(FailResult("readRepresentative failed:\n" + f.reason))
      case Right(containers) => {

        if (containerId >= 0 && containerId < containers.length) {
          val result = containers(containerId).readRepresentative(suiteId)
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
   * Write new content to reps. held by a set of containers. May fail if a single representative cannot be found
   * @param containerIds set of containers that are addressed
   * @param suiteId ID of the rep. that is to be written to
   * @param newContent new integer content that is to be written
   * @param increment flag to determine if version number should be incremented
   * @return either failure or the latency for this container
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
            case Right(result) => {
              if (result > latency) {
                latency = result
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
    val newSystem = new FileSystem()
    newSystem.createContainers(numContainers, latencies, newFailProbs)
    newSystem
  }
}
