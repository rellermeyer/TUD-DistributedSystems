package chubby.common

class Lock(var identifier: String, var leaseStart: Long, var leaseDuration: Long, var write: Boolean = false) {
  def isExpired: Boolean = {
    val currentTimestamp: Long = System.currentTimeMillis / 1000;
    currentTimestamp >= this.leaseStart + this.leaseDuration;
  }
}
