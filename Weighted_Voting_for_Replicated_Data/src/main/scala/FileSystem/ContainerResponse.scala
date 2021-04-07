package FileSystem

class ContainerResponse(newCid: Int, newLatency: Int, newWeight: Int, newPrefix: Prefix) {

  private val _cid: Int = newCid
  private val _latency: Int = newLatency
  private val _weight:Int = newWeight
  private val _prefix: Prefix = newPrefix

  /**
   * accessor methods
   */
  def cid: Int = _cid
  def latency: Int = _latency
  def weight: Int = _weight
  def prefix: Prefix = _prefix

  //TODO: print prefix?
  override def toString: String = {
    " (cid:" + cid + " lat:" + latency + " wght:" + weight + ")"
  }
}

object ContainerResponse {
  def apply(newCid: Int, newLatency: Int, newWeight: Int, newPrefix: Prefix): ContainerResponse = {
    val newResponse = new ContainerResponse(newCid, newLatency, newWeight, newPrefix)
    newResponse
  }
}
