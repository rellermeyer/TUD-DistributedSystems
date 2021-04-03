package VotingSystem

import FileSystem.{ContainerResponse, FileSystem, Representative}

import scala.util.control.Breaks.{break, breakable}

class FileSuite (fileSystem: FileSystem, newSuiteId: Int){

  case class FailResult(reason:String)

  /**
   * constructor
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
   * accessor methods
   */
  def suiteId: Int = _suiteId

  /**
   * Returning the latest container response, and checks if there are responses at all
   * @return latest containerResponse
   */
  def findLatest(responses: Seq[ContainerResponse]): Either[FailResult, ContainerResponse] = {
    if (responses.nonEmpty) {
      Right(responses.maxBy(_.prefix.versionNumberTentative))
    }
    else {
      Left(FailResult("findLatest failed: no container responses present in file system response"))
    }
  }

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
   * Distinguishes the containers that meet the read quorum and are therefore readCandidates
   * @param r
   * @param versionNumber
   * @return readCandidates
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
   * Distinguishes the containers that meet the write quorum and are therefore writeCandidates
   * @param w
   * @param versionNumber
   * @return writeCandidates
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


  def createFileSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
    val result = fileSystem.createRepresentatives(suiteId, suiteR, suiteW, repWeights)

    result match {
      case Left(f) => Left(FailResult("createFSuite failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }


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
   * Function that finds the most suitable read response by first checking all container responses,
   * then computing the reading quorum, check if the container responses meet the reading quorum
   * and thus are suitable read candidates. Lastly pick the candidate with the lowest response time.
   * @param
   * @return result
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
   * Function that finds the most suitable write response by first checking all container responses,
   * then computing the writing quorum, check if the container responses meet the writing quorum
   * and thus are suitable write candidates. Lastly pick the candidate with the lowest response time.
   * @param
   * @return result
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
