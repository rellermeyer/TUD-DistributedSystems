package FileSystem

class Representative(suiteId: Int, suiteR: Int, suiteW: Int, suiteInfo: Seq[Int], repWeight: Int) {

  /**
   * private class fields
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

  /**
   * overwrite the tentative content of the representative during a transaction
   * @param newContent the new content to be written
   */
  def writeTentative(newContent: Int): Unit = {
    _contentTentative = newContent
  }

  /**
   * Increment the tentative version number of the representative during a transaction
   */
  def incrementNumber(): Unit = {
    _prefix.versionNumberTentative = _prefix.versionNumberTentative + 1
  }

  /**
   * Set tentative content and prefix state at the start of a new transaction
   */
  def initTentativeRep(): Unit = {
    _contentTentative = _content
    prefix.initTentativePrefix()
  }

  /**
   * Set definitive content and prefix state when committing a transaction
   */
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