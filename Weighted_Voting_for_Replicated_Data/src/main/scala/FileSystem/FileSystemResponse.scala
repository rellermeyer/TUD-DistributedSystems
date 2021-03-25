package FileSystem

class FileSystemResponse {

  case class FailResult(reason:String)

  private var _containerResponses: Seq[ContainerResponse] = Seq.empty[ContainerResponse]

  def containerResponses: Seq[ContainerResponse] = _containerResponses

  /**
   * Adding a response to the total list of responding containers
   * @param ContainerResponses
   * @return _containerResponses
   */
  def addResponse(newResponse: ContainerResponse): Unit = {
    _containerResponses = _containerResponses :+ newResponse
  }

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
