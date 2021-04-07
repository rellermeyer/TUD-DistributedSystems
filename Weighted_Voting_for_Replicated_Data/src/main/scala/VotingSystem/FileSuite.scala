package VotingSystem

import FileSystem.{ContainerResponse, FileSystem, Representative}

import scala.util.control.Breaks.{break, breakable}

class FileSuite (fileSystem: FileSystem, newSuiteId: Int){

  /**
   * Case class for handling failed method calls
   * @param reason a textual explanation of the reason for failure
   */
  case class FailResult(reason:String)

  /**
   * Private class fields
   */
  private val _suiteId: Int = newSuiteId
  private var _r: Int = -1
  private var _w: Int = -1
  private var _versionNumber = -1
  private var _suiteInfo: Seq[Int] = Seq.empty[Int]
  private var _inquiryResponse: Seq[ContainerResponse] = Seq.empty[ContainerResponse]

  private var _hasInquired: Boolean = false
  private var _hasIncremented: Boolean = false


  /**
   * accessor method
   */
  def suiteId: Int = _suiteId

  /**
   * Return a most up-to-date container response from a set of responses. May fail if there are no responses at all
   * @param responses the set of responses from containers
   * @return either failure or one response that is up-to-date
   */
  def findLatest(responses: Seq[ContainerResponse]): Either[FailResult, ContainerResponse] = {
    if (responses.nonEmpty) {
      Right(responses.maxBy(_.prefix.versionNumberTentative))
    }
    else {
      Left(FailResult("findLatest failed: no container responses present in file system response"))
    }
  }

  /**
   * Inquire with the file system what representatives of a file suite can be found
   * Then set FileSuite fields to most up-to-date values if a read quorum can be established
   * @return either failure or the latency of the latest response
   */
  def initiateInquiries(): Either[FailResult, Int] = {
    val response = fileSystem.collectRepresentatives(_suiteId)

    response match {
      case Left(f) => Left(FailResult("inquiry failed:\n" + f.reason))
      case Right(response) => {

        if (!response._1.isEmpty) {
          _inquiryResponse = response._1
          val latest = findLatest(response._1)

          latest match {
            case Left(f) => Left(FailResult("inquiry failed:\n" + f.reason))
            case Right(latest) => {
              _r = latest.prefix.r
              _w = latest.prefix.w
              _versionNumber = latest.prefix.versionNumberTentative
              _suiteInfo = latest.prefix.info

              val quorum = collectReadQuorum(response._1)
              quorum match {
                case Left(f) => Left(FailResult("inquiry failed:\n" + f.reason))
                case Right(quorum) => {
                  _hasInquired = true
                  Right(response._2)
                }
              }
            }
          }
        }
        else {
          Left(FailResult("inquiry failed: no responses received"))
        }
      }
    }
  }


  /**
   * Gather responses from containers to establish a read quorum
   * @param responses responses from the inquiry
   * @return either failure or a tuple of the containers that satisfy the quorum and the latency of gathering the quorum
   */
  def collectReadQuorum(responses: Seq[ContainerResponse]): Either[FailResult, (Seq[ContainerResponse], Int)] = {
    val currentReps: Seq[ContainerResponse] = responses.sortBy(_.latency)
    var readCandidates: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
    var totalWeight: Int = 0

    for (rep <- currentReps) {
      readCandidates = readCandidates :+ rep
      totalWeight += rep.weight
      if (totalWeight >= _r) {
        return Right(readCandidates, readCandidates.last.latency)
      }
    }
    Left(FailResult("findReadQuorum failed: no quorum present"))
  }

  /**
   * Gather responses from containers to establish a write quorum
   * @param responses responses from the inquiry
   * @return either failure or a tuple of the containers that satisfy the quorum and the latency of gathering the quorum
   */
  def collectWriteQuorum(responses: Seq[ContainerResponse]): Either[FailResult, (Seq[ContainerResponse], Int)] = {
    val currentReps: Seq[ContainerResponse] = responses.filter(_.prefix.versionNumberTentative == _versionNumber).sortBy(_.latency)
    var writeCandidates: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
    var totalWeight: Int = 0

    for (rep <- currentReps) {
      writeCandidates = writeCandidates :+ rep
      totalWeight += rep.weight
      if (totalWeight >= _w) {
        return Right(writeCandidates, writeCandidates.last.latency)
      }
    }
    Left(FailResult("collectWriteQuorum failed: no quorum present"))
  }

  /**
   * Instantiate a new file suite in the file system
   * @param suiteId ID of the new suite
   * @param suiteR r value of the new suite
   * @param suiteW w value of the new suite
   * @param repWeights rep. weights of the reps. of the new suite
   * @return either failure or nothing
   */
  def createFileSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
    val result = fileSystem.createRepresentatives(suiteId, suiteR, suiteW, repWeights)

    result match {
      case Left(f) => Left(FailResult("createFileSuite failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }

  /**
   * Delete a file suite residing in the file system
   * @param suiteId ID of the suite to be deleted
   * @return either failure or nothing
   */
  def deleteFileSuite(suiteId: Int): Either[FailResult, Unit] = {
    val result = fileSystem.deleteRepresentatives(suiteId)

    result match {
      case Left(f) => Left(FailResult("deleteFileSuite failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }

  /**
   * Find the representative in a read quorum that is both up-to-date, and with the lowest container latency
   * @param responses container responses in a read quorum
   * @return either failure or the fastest current representative
   */
  def selectFastestCurrentRepresentative(responses: Seq[ContainerResponse]): Either[FailResult, ContainerResponse] = {
    var foundCandidate: Boolean = false
    var readCandidate: ContainerResponse = null
    breakable { for (c <- responses) {
      if (c.prefix.versionNumberTentative == _versionNumber) {
        readCandidate = c
        foundCandidate = true
        break
      }
    } }
    if (foundCandidate) {
      Right(readCandidate)
    }
    else {
      Left(FailResult("selectFastestCurrentRepresentative failed: suitable candidate present in response set"))
    }
  }


  /**
   * Read the content of a file suite
   * Initiate inquiries first if not already done so, then gather a read quorum and find a suitable read candidate
   * @return either failure or a tuple containing file suite content and total latency
   */
  def read(): Either[FailResult, (Int, Int)] = {
    var latency: Int = 0

    if (!_hasInquired) {
      val result = initiateInquiries()
      result match {
        case Left(f) => Left(FailResult("read failed:\n" + f.reason))
        case Right(result) => latency = latency + result
      }
    }

    val responses = fileSystem.collectRepresentatives(_suiteId)
    var validResponses = Seq.empty[ContainerResponse]
    responses match {
      case Left(f) => Left(FailResult("read failed:\n" + f.reason))
      case Right(responses) => {

        for (c <- responses._1) {
          if (_inquiryResponse.exists(e => e.cid == c.cid))
            validResponses = validResponses :+ c
        }
      }
    }

    val quorum = collectReadQuorum(validResponses)
    quorum match {
      case Left(f) => Left(FailResult("read failed:\n" + f.reason))
      case Right(quorum) => {

        latency = latency + quorum._2
        val readCandidate = selectFastestCurrentRepresentative(quorum._1)

        readCandidate match {
          case Left(f) => Left(FailResult("read failed:\n" + f.reason))
          case Right(readCandidate) => {

            val result = fileSystem.readRepresentative(readCandidate.cid, _suiteId)

            result match {
              case Left(f) => Left(FailResult("read failed:\n" + f.reason))
              case Right(result) => {

                latency = latency + result._2
                Right(result._1, latency)
              }
            }
          }
        }
      }
    }
  }


  /**
   * Write new content to a file suite
   * Initiate inquiries first if not already done so, then gather a write quorum and write to all members
   * @return either failure or the total latency
   */
  def write(newContent: Int): Either[FailResult, Int] = {
    var latency: Int = 0

    if (!_hasInquired) {
      val result = initiateInquiries()
      result match {
        case Left(f) => Left(FailResult("write failed:\n" + f.reason))
        case Right(result) => latency = latency + result
      }
    }

    val responses = fileSystem.collectRepresentatives(_suiteId)
    var validResponses = Seq.empty[ContainerResponse]
    responses match {
      case Left(f) => Left(FailResult("write failed:\n" + f.reason))
      case Right(responses) => {

        for (c <- responses._1) {
          if (_inquiryResponse.exists(e => e.cid == c.cid)) {
            validResponses = validResponses :+ c
          }
        }
      }
    }

    val quorum = collectWriteQuorum(validResponses)
    quorum match {
      case Left(f) => Left(FailResult("write failed:\n" + f.reason))
      case Right(quorum) => {

        latency = latency + quorum._2
        var cids: Seq[Int] = Seq.empty[Int]
        for (c <- quorum._1) {
          cids = cids :+ c.cid
        }

        val result = fileSystem.writeRepresentatives(cids, _suiteId, newContent, !_hasIncremented)

        result match {
          case Left(f) => Left(FailResult("write failed:\n" + f.reason))
          case Right(result) => {
            latency = latency + result
            if (!_hasIncremented) {
              _hasIncremented = true
              _versionNumber += 1
            }
            Right(latency)
          }
        }
      }
    }
  }
}
