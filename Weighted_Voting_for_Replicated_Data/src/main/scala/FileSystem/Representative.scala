package FileSystem

class Representative(suiteId: Int, suiteR: Int, suiteW: Int, suiteInfo: Seq[Int], repWeight: Int) {

  /**
   * constructor
   */
  private val _repId: Int = suiteId
  private val _weight: Int = repWeight
  private var _content: Int = -1
  private var _contentTentative: Int = -1
  private val _prefix: Prefix = Prefix(suiteR, suiteW, suiteInfo)

  /**
   * accessor methods
   */
  def repId: Int = _repId
  def weight: Int = _weight
  def contentTentative: Int = _contentTentative
  def prefix: Prefix = _prefix

  /**
   * mutator methods
   */
  def contentTentative_=(newContent: Int): Unit =  _contentTentative = newContent

  def writeTentative(newContent: Int): Unit = {
    _contentTentative = newContent
  }

  def incrementNumber(): Unit = {
    _prefix.versionNumberTentative = _prefix.versionNumberTentative + 1
  }

  def initTentativeRep(): Unit = {
    _contentTentative = _content
    prefix.initTentativePrefix()
  }

  def commitTentativeRep(): Unit = {
    _content = _contentTentative
    prefix.commitTentativePrefix()
  }
}

/**
 * companion object
 */
object Representative {
  def apply(suiteId: Int, suiteR: Int, suiteW: Int, suiteInfo: Seq[Int], repWeight: Int): Representative = {
    val newRep = new Representative(suiteId, suiteR, suiteW, suiteInfo, repWeight)
    newRep
  }
}