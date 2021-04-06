package sgrub.contracts

/**
 * Represents the Data User consuming data updates by key (should be a smart contract?)
 */
trait DataUser {


  def gGet(key: Long, callback: (Long, Array[Byte]) => Unit): Unit
}
