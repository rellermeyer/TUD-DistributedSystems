package FileSystem

class FileSystemResponse {

  var _containerResponses: Seq[ContainerResponse] = Seq.empty[ContainerResponse]

  def addResponse(newResponse: ContainerResponse): Unit = {
    _containerResponses = _containerResponses :+ newResponse
  }

  def findLatest(): ContainerResponse = {
    _containerResponses.maxBy(_.prefix.versionNumber)
  }

  def findReadQuorum(r: Int, versionNumber: Int): Int = {
    val currentReps: Seq[ContainerResponse] = _containerResponses.sortBy(_.latency)
    var totalWeight: Int = 0
    var readCandidate: Int = -1
    var foundCandidate: Boolean = false
    for (c <- currentReps) {
      totalWeight += c.weight
      if (c.prefix.versionNumber == versionNumber && !foundCandidate) {
        readCandidate = c.cid
      }
      if (totalWeight >= r) {
        println("quorum present")
        return readCandidate
      }
    }
    println("no quorum present")
    readCandidate
  }

  def findWriteQuorum(w: Int, versionNumber: Int): Seq[Int] = {
    val currentReps: Seq[ContainerResponse] = _containerResponses.filter(_.prefix.versionNumber == versionNumber).sortBy(_.latency)
    var writeQuorum: Seq[Int] = Seq.empty[Int]
    var totalWeight: Int = 0
    for (c <- currentReps) {
      writeQuorum = writeQuorum :+ c.cid
      totalWeight += c.weight
      println("new rep added: cid " + c.cid + ", weight " + c.weight)
      if (totalWeight >= w) {
        println("quorum present")
        return writeQuorum
      }
    }
    println("no quorum present")
    Seq(-1)
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
