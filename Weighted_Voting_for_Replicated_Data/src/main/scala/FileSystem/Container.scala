package FileSystem

class Container(newLatency: Int) {

  /**
   * constructor
   */
  case class FailResult(reason:String)

  private val _latency: Int = newLatency
  private var _representatives: Seq[Representative] = scala.collection.immutable.Vector.empty

  /**
   * accessor methods
   */
  def latency: Int = _latency
  def representatives: Seq[Representative] = _representatives

  def findRepresentative(suiteId: Int): Option[Representative] = {
    _representatives.find(element => element.repId == suiteId)
  }

  def createRepresentative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int): Either[FailResult, Unit] = {
    if (findRepresentative(suiteId).isEmpty) {
      val newRepresentative = Representative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int)
      _representatives = _representatives :+ newRepresentative
      Right()
    }
    else {
      Left(FailResult("createRepresentative failed: a representative of file " + suiteId + " exists already"))
    }
  }

  def readRepresentative(suiteId: Int): Either[FailResult, Int] = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
      Right(rep.get.content)
    }
    else {
      Left(FailResult("readRepresentative failed: no representative of file " + suiteId + " exists in container"))
    }
  }

  def writeRepresentative(suiteId: Int, newContent: Int): Either[FailResult, Unit] = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
      //println("write rep: " + rep.get.content + " overwritten by " + newContent)
      rep.get.content = newContent
      rep.get.prefix.versionNumber += 1
      //println("new version number: " + rep.get.prefix.versionNumber)
      Right()
    }
    else {
      Left(FailResult("writeRepresentative failed: no representative of file " + suiteId + " exists in container"))
    }
  }
}

/**
 * companion object
 */
object Container {
  def apply(newLatency: Int): Container = {
    val newContainer = new Container(newLatency)
    newContainer
  }
}
