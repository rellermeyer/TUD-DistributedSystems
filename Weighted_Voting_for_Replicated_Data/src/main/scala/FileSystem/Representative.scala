package FileSystem

class Representative(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int) {

  /**
   * constructor
   */
  private val _repId: Int = suiteId
  private val _weight: Int = repWeight
  private var _content: Int = -1
  private val _prefix: Prefix = Prefix(suiteR, suiteW)

  /**
   * accessor methods
   */
  def repId: Int = _repId
  def weight: Int = _weight
  def content: Int = _content
  def prefix: Prefix = _prefix

  /**
   * mutator methods
   */
  def content_=(newContent: Int): Unit =  _content = newContent
}

/**
 * companion object
 */
object Representative {
  def apply(suiteId: Int, suiteR: Int, suiteW: Int, repWeight: Int): Representative = {
    val newRep = new Representative(suiteId, suiteR, suiteW, repWeight)
    newRep
  }
}