package VotingSystem

import FileSystem.{ContainerResponse, DistributedSystem, FileSystemResponse, Representative}

import scala.util.control.Breaks.{break, breakable}

class FileSuite (fileSystem: DistributedSystem, newSuiteId: Int){

  case class FailResult(reason:String)

  /**
   * constructor
   */
  private val _suiteId: Int = newSuiteId
  private val _fsResp:  Either[fileSystem.FailResult, FileSystemResponse] = fileSystem.collectRepresentatives(_suiteId)

  /**
   * accessor methods
   */
  def suiteId: Int = _suiteId

  /**
   * Returning the latest container response, and checks if there are responses at all
   * @return latest containerResponse
   */
  def inquiry(fsResp: FileSystemResponse): Either[FailResult, ContainerResponse] = {
    if (fsResp.containerResponses.nonEmpty) {
      Right(fsResp.containerResponses.maxBy(_.prefix.versionNumber))
    }
    else {
      Left(FailResult("findLatest failed: no container responses present in file system response"))
    }
  }


  /**
   * Distinguishes the containers that meet the read quorum and are therefore readCandidates
   * @param r
   * @param versionNumber
   * @return readCandidates
   */
  def findReadQuorum(fsResp: FileSystemResponse, r: Int, versionNumber: Int): Either[FailResult, (Seq[ContainerResponse], Int)] = {
    val currentReps: Seq[ContainerResponse] = fsResp.containerResponses.sortBy(_.latency)
    var readCandidates: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
    var totalWeight: Int = 0

    for (rep <- currentReps) {
      readCandidates = readCandidates :+ rep
      totalWeight += rep.weight
      if (totalWeight >= r) {
        return Right((readCandidates, readCandidates.last.latency))
      }
    }
    Left(FailResult("findReadQuorum failed: no quorum present"))
  }


  //def copyRepresentative(source: Representative): Either[FailResult, (Representative, Int)] = {
  //  val newCopy: Representative = Representative()
  //}

  // TODO: does this work as intended w.r.t. up to date copies?
  /**
   * Distinguishes the containers that meet the write quorum and are therefore writeCandidates
   * @param w
   * @param versionNumber
   * @return writeCandidates
   */
  def findWriteQuorum(fsResp: FileSystemResponse, w: Int, versionNumber: Int): Either[FailResult, (Seq[ContainerResponse], Int)] = {
    val currentReps: Seq[ContainerResponse] = fsResp.containerResponses.filter(_.prefix.versionNumber == versionNumber).sortBy(_.latency)
    var writeCandidates: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
    var totalWeight: Int = 0
    for (rep <- currentReps) {
      writeCandidates = writeCandidates :+ rep
      totalWeight += rep.weight
      if (totalWeight >= w) {
        return Right(writeCandidates, writeCandidates.last.latency)
      }
    }
    Left(FailResult("findWriteQuorum failed: no quorum present")) //TODO: own error class?
  }


  def createFileSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
    val result = fileSystem.createRepresentatives(suiteId, suiteR, suiteW, repWeights)

    result match {
      case Left(f) => Left(FailResult("createFSuite failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }


  def findReadCandidate(readQuorum: Seq[ContainerResponse], versionNumber: Int): Either[FailResult, ContainerResponse] = {
    var foundCandidate: Boolean = false
    var readCandidate: ContainerResponse = null
    breakable { for (c <- readQuorum) {
      if (c.prefix.versionNumber == versionNumber) {
        readCandidate = c
        foundCandidate = true
        break
      }
    } }
    if (foundCandidate) {
      Right(readCandidate)
    }
    else {
      Left(FailResult("findReadCandidate failed: no read candidate present in read quorum"))
    }
  }


  /**
   * Function that finds the most suitable read response by first checking all container responses,
   * then computing the reading quorum, check if the container responses meet the reading quorum
   * and thus are suitable read candidates. Lastly pick the candidate with the lowest response time.
   * @param
   * @return result
   */
  def readSuite(): Either[FailResult, (Int, Int)] = {
    _fsResp match {
      case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
      case Right(responses) => {
        val current = inquiry(responses)

        current match {
          case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
          case Right(current) => {
            val quorum = findReadQuorum(responses, current.prefix.r, current.prefix.versionNumber)

            quorum match {
              case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
              case Right(quorum) => {
                val readCandidate = findReadCandidate(quorum._1, current.prefix.versionNumber)

                readCandidate match {
                  case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
                  case Right(readCandidate) => {
                    val result = fileSystem.readRepresentative(readCandidate.cid, _suiteId)

                    result match {
                      case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
                      case Right(result) => {
                        val latency: Int = quorum._2 + result._2
                        Right(result._1, latency)
                      }
                    }
                  }
                }
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
  def writeSuite(newContent: Int): Either[FailResult, Int] = {
    _fsResp match {
      case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
      case Right(responses) => {
        val current = inquiry(responses)

        current match {
          case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
          case Right(current) => {
            val quorum = findWriteQuorum(responses, current.prefix.w, current.prefix.versionNumber)

            quorum match {
              case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
              case Right(quorum) => {
                var cids: Seq[Int] = Seq.empty[Int]
                for (r <- quorum._1) {
                  cids = cids :+ r.cid
                }
                val result = fileSystem.writeRepresentatives(cids, _suiteId, newContent)

                result match {
                  case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
                  case Right(result) => {
                    val latency: Int = quorum._2 + result
                    Right(latency)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
