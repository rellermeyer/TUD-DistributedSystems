package sgrub.contracts

import scorex.crypto.authds.ADDigest

/**
 * Represents the Data Owner producing a stream of data updates
 */
trait DataOwner {
  def latestDigest: ADDigest

  /**
   * Runs the ADS protocol with SP
   *
   * @param kvs The key-value pairs to be updated
   * @return True when the KVs were updated successfully and securely, otherwise returns False
   */
  def gPuts(kvs: Map[Long, Array[Byte]]): Boolean
}
