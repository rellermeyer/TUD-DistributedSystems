class Representative(suiteId: Int, repWeight: Int, suiteR: Int, suiteW: Int) {

  /**
   * constructor
   */
  private val _repId: Int = suiteId
  private val _weight: Int = repWeight
  private var _content: Int = -1
  private val _prefix: Prefix = Prefix(suiteR, suiteW)

  /**
   * accessor methods
   * allows for access of private properties through the syntax: instanceName.repId
   */
  def repId: Int = _repId
  def weight: Int = _weight
  def content: Int = _content
  def prefix: Prefix = _prefix

  /**
   * mutator methods
   * allows for mutation of private properties through the syntax: instanceName.content = newContent,
   * as if the property content is publicly accessible
   * this syntax is actually a shorthand for: instanceName.content_=(newContent), but the parenthese can be dropped
   * and the underscore replaced by whitespace
   */
  def content_= (newContent: Int): Unit =  _content = newContent
}

/**
 * companion object
 * functions somewhat as a constructor for the corresponding class if you use it to set public properties of the class
 * allows for instance creation through the syntax: val instanceName = Representative(parameters)
 */
object Representative {
  def apply(suiteId: Int, repWeight: Int, suiteR: Int, suiteW: Int): Representative = {
    val newRep = new Representative(suiteId, repWeight, suiteR, suiteW)
    newRep
  }
}