package FileSystem

class Container(newLatency: Int) {

  /**
   * constructor
   */
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

  def createRepresentative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int): Unit = {
    if (findRepresentative(suiteId).isEmpty) {
      val newRepresentative = Representative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int)
      _representatives = _representatives :+ newRepresentative
      println("container creates representative with weight " + repWeight)
    }
    else {
      println("Representative exists already, TODO: better error handling") //TODO: better error handling
    }
  }

  def readRepresentative(suiteId: Int): Int = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
	    println("read rep:" + rep.get.content)
      rep.get.content
    }
    else {
      println("rep does not exist")
      -1
    }
  }

  def writeRepresentative(suiteId: Int, newContent: Int): Unit = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
      println("write rep: " + rep.get.content + " overwritten by " + newContent)
      rep.get.content = newContent
      rep.get.prefix.versionNumber += 1
      println("new version number: " + rep.get.prefix.versionNumber)
    }
    else {
      println("rep does not exist")
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
