package FileSystem

class Container(newContainerId: Int, newLatency: Int) {

  /**
   * constructor
   */
  private val _containerId: Int = newContainerId //TODO: may not be needed
  private val _latency: Int = newLatency
  private var _representatives: Vector[Representative] = scala.collection.immutable.Vector.empty

  /**
   * accessor methods
   */
  def containerId: Int = _containerId
  def latency: Int = _latency
  def representatives: Vector[Representative] = _representatives

  def findRepresentative(suiteId: Int): Option[Representative] = {
    _representatives.find(element => element.repId == suiteId)
  }

  def createRepresentative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int): Unit = {
    if (findRepresentative(suiteId).isEmpty) {
      val newRepresentative = Representative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int)
      _representatives = _representatives :+ newRepresentative
      println("container " + _containerId + " creates representative with weight " + repWeight)
    }
    else {
      println("Representative exists already, TODO: better error handling") //TODO: better error handling
    }
  }

  def readRepresentative(suiteId: Int): Unit = {
    val rep = findRepresentative(suiteId)
    if (rep.isDefined) {
	    println("read rep:" + rep.get.content)
      //rep.get.content
    }
    else {
      println("rep does not exist")
    }
  }

  def writeRepresentative(suiteId: Int): Unit = {
    // TODO: check if fileSuite exists

  }
}

/**
 * companion object
 */
object Container {
  def apply(newContainerId: Int, newLatency: Int): Container = {
    val newContainer = new Container(newContainerId, newLatency)
    newContainer
  }
}
