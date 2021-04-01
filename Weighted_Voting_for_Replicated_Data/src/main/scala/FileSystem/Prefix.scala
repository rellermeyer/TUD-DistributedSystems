package FileSystem

class Prefix(suiteR: Int, suiteW: Int, suiteInfo: Seq[Int]){

  /**
   * constructor
   */
  private var _versionNumber: Int = 0
  private var _versionNumberTentative: Int = 0
  private val _r: Int = suiteR
  private val _w: Int = suiteW
  private val _info: Seq[Int] = suiteInfo

  /**
   * accessor methods
   */
  //def versionNumber: Int = _versionNumber
  def versionNumberTentative: Int = _versionNumberTentative
  def r: Int = _r
  def w: Int = _w
  def info: Seq[Int] = _info

  /**
   * mutator methods
   */
  def versionNumberTentative_=(newNumber: Int): Unit =  _versionNumberTentative = newNumber

  def initTentativePrefix(): Unit = {
    _versionNumberTentative = _versionNumber
  }

  def commitTentativePrefix(): Unit = {
    _versionNumber = _versionNumberTentative
  }
}

/**
 * companion object
 */
object Prefix {
  def apply(suiteR: Int, suiteW: Int, suiteInfo: Seq[Int]): Prefix = {
    val newPrefix = new Prefix(suiteR, suiteW, suiteInfo)
    newPrefix
  }
}