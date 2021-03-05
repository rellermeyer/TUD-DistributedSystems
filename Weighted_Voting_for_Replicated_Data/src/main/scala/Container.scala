class Container (newContainerId: Int) {

  /**
   * constructor
   */
  private val _containerId: Int = newContainerId
  private var _representatives: Vector[Representative] = scala.collection.immutable.Vector.empty

  /**
   * accessor methods
   */
  def containerId: Int = _containerId
  def representatives: Vector[Representative] = _representatives

  def createRepresentative (suiteId: Int, repWeight: Int, suiteR: Int, suiteW: Int): Unit = {
    // TODO: check if fileSuite does not exist already
    val newRepresentative = Representative(suiteId: Int, repWeight: Int, suiteR: Int, suiteW: Int)
    _representatives = _representatives :+ newRepresentative
  }

}

/**
 * companion object
 */
object Container {
  def apply(newContainerId: Int): Container = {
    val newContainer = new Container(newContainerId)
    newContainer
  }
}
