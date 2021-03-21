package VotingSystem

import FileSystem.{ContainerResponse, DistributedSystem, FileSystemResponse, Representative}

import scala.util.control.Breaks.{break, breakable}

class FileSuite (fileSystem: DistributedSystem, newSuiteId: Int){

  /**
   * constructor
   */
  case class FailResult(reason:String)

  //private val _fileSystem: DistributedSystem = newFileSystem
  private val _suiteId: Int = newSuiteId
  private val _fsResp:  Either[fileSystem.FailResult, FileSystemResponse] = fileSystem.collectRepresentatives(_suiteId)

  /**
   * accessor methods
   */
  def suiteId: Int = _suiteId

  //TODO: kan dit weg :P?
  //def suiteR: Int = _fsResp.findLatest().prefix.r
  //def suiteW: Int = _fsResp.findLatest().prefix.w

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
        val current = responses.findLatest()

        current match {
          case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
          case Right(current) => {
            val quorum = responses.findReadQuorum(current.prefix.r, current.prefix.versionNumber)

            quorum match {
              case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
              case Right(quorum) => {
                val readCandidate = findReadCandidate(quorum, current.prefix.versionNumber)

                readCandidate match {
                  case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
                  case Right(readCandidate) => {
                    val result = fileSystem.readRepresentative(readCandidate.cid, _suiteId)

                    result match {
                      case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
                      case Right(result) => {
                        val latency: Int = quorum.last.latency + readCandidate.latency
                        Right(result, latency)
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
        val current = responses.findLatest()

        current match {
          case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
          case Right(current) => {
            val quorum = responses.findWriteQuorum(current.prefix.w, current.prefix.versionNumber)

            quorum match {
              case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
              case Right(quorum) => {
                var cids: Seq[Int] = Seq.empty[Int]
                for (r <- quorum) {
                  cids = cids :+ r.cid
                }
                val result = fileSystem.writeRepresentatives(cids, _suiteId, newContent)

                result match {
                  case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
                  case Right(r) => {
                    val latency: Int = quorum.last.latency + quorum.last.latency
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
