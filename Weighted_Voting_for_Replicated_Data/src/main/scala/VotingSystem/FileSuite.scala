package VotingSystem

import FileSystem.{ContainerResponse, DistributedSystem, FileSystemResponse}

class FileSuite (fileSystem: DistributedSystem, newSuiteId: Int){

  /**
   * constructor
   */
  case class FailResult(reason:String)

  //private val _fileSystem: DistributedSystem = newFileSystem
  private val _suiteId: Int = newSuiteId
  private val _fsResp:  Either[fileSystem.FailResult, FileSystemResponse] = fileSystem.collectSuite(_suiteId)

  /**
   * accessor methods
   */
  def suiteId: Int = _suiteId
  //def suiteR: Int = _fsResp.findLatest().prefix.r
  //def suiteW: Int = _fsResp.findLatest().prefix.w

  def createFSuite(suiteId: Int, suiteR: Int, suiteW: Int, repWeights: Seq[Int]): Either[FailResult, Unit] = {
    val result = fileSystem.createSuite(suiteId, suiteR, suiteW, repWeights)

    result match {
      case Left(f) => Left(FailResult("createFSuite failed:\n" + f.reason))
      case Right(r) => Right()
    }
  }

  def suiteRead(): Either[FailResult, Int] = {
    _fsResp match {
      case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
      case Right(responses) => {
        val current = responses.findLatest()

        current match {
          case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
          case Right(current) => {
            val cid = responses.findReadQuorum(current.prefix.r, current.prefix.versionNumber)

            cid match {
              case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
              case Right(cid) => {
                val result = fileSystem.readSuite(cid, _suiteId)

                result match {
                  case Left(f) => Left(FailResult("suiteRead failed:\n" + f.reason))
                  case Right(result) => Right(result)
                }
              }
            }
          }
        }
      }
    }
  }

  def suiteWrite(newContent: Int): Either[FailResult, Unit] = {
    _fsResp match {
      case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
      case Right(responses) => {
        val current = responses.findLatest()

        current match {
          case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
          case Right(current) => {
            val cids = responses.findWriteQuorum(current.prefix.w, current.prefix.versionNumber)

            cids match {
              case Left(f) => Left(FailResult("suiteWrite failed:\n" + f.reason))
              case Right(cid) => {
                val result = fileSystem.writeSuite(cid, _suiteId, newContent)
                Right()
              }
            }
          }
        }
      }
    }
  }
}
