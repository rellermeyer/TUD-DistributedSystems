package FileSystem

import scala.util.control.Breaks.{break, breakable}

class FileSystemResponse {

  case class FailResult(reason:String)

  var _containerResponses: Seq[ContainerResponse] = Seq.empty[ContainerResponse]

  def addResponse(newResponse: ContainerResponse): Unit = {
    _containerResponses = _containerResponses :+ newResponse
  }

  def findLatest(): Either[FailResult, ContainerResponse] = {
    if (_containerResponses.nonEmpty) {
      Right(_containerResponses.maxBy(_.prefix.versionNumber))
    }
    else {
      Left(FailResult("findLatest failed: no container responses present"))
    }
  }

  def findReadQuorum(r: Int, versionNumber: Int): Either[FailResult, Seq[ContainerResponse]] = {
    val currentReps: Seq[ContainerResponse] = _containerResponses.sortBy(_.latency)
    var readCandidates: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
    var totalWeight: Int = 0

    for (rep <- currentReps) {
      readCandidates = readCandidates :+ rep
      totalWeight += rep.weight
      //println("new rep added: cid " + c.cid + ", weight " + c.weight)
      if (totalWeight >= r) {
        return Right(readCandidates)
      }
    }
    Left(FailResult("findReadQuorum failed: no quorum present")) //TODO: own error class?
  }

  // TODO: does this work as intended w.r.t. up to date copies?
  def findWriteQuorum(w: Int, versionNumber: Int): Either[FailResult, Seq[ContainerResponse]] = {
    val currentReps: Seq[ContainerResponse] = _containerResponses.filter(_.prefix.versionNumber == versionNumber).sortBy(_.latency)
    var writeCandidates: Seq[ContainerResponse] = Seq.empty[ContainerResponse]
    var totalWeight: Int = 0

    for (rep <- currentReps) {
      writeCandidates = writeCandidates :+ rep
      totalWeight += rep.weight
      //println("new rep added: cid " + c.cid + ", weight " + c.weight)
      if (totalWeight >= w) {
        return Right(writeCandidates)
      }
    }
    Left(FailResult("findWriteQuorum failed: no quorum present")) //TODO: own error class?
  }

  //TODO: print prefix?
  override def toString: String = {
    var outputString: String = ""
    for (c <- _containerResponses) {
      outputString = outputString + c.toString
    }
    outputString
  }
}

object FileSystemResponse {
  def apply(): FileSystemResponse = {
    val newResponse = new FileSystemResponse()
    newResponse
  }
}
