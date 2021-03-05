class Prefix (suiteR: Int, suiteW: Int){

  /**
   * constructor
   */
  private var _versionNumber: Int = 1
  private val _r: Int = suiteR
  private val _w: Int = suiteW
  //private val suiteSize: Int = is this needed?
  //TODO: list of representatives. Not sure if needed either

  /**
   * accessor methods
   */
  def versionNumber: Int = _versionNumber
  def r: Int = _r
  def w: Int = _w

  /**
   * mutator methods
   */
  def versionNumber_= (newNumber: Int): Unit =  _versionNumber = newNumber
}

/**
 * companion object
 */
object Prefix {
  def apply(suiteR: Int, suiteW: Int): Prefix = {
    val newPrefix = new Prefix(suiteR, suiteW)
    newPrefix
  }
}