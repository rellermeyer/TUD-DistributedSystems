package sgrub.contracts
import scorex.crypto.authds.{ADDigest, SerializedAdProof}

/**
 * Represents the off-chain data storage
 */
trait StorageProvider {

  /**
   * The initial digest for the prover
   */
  val initialDigest: ADDigest

  /**
   * Stores a batch of key-value pairs ([[kvs]]) and returns the digest and proof for the operation
   * @param kvs The batch of key-value pairs to insert or update
   * @return Returns the digest and proof for the operation
   */
  def gPuts(kvs: Map[Long, Array[Byte]]): (ADDigest, SerializedAdProof)

  /// NOTE: This should actually be calling with/returning a proof and digest via separate callbacks. This is just for initial tests.
  /**
   * Queries the key-value store and calls [[callback]] with a proof if it exists
   * @param key The key with which to query the key-value store
   * @param callback An implicit function to call with the value associated with the key if it exists
   */
  def request(key: Long, callback: SerializedAdProof => Unit): Unit
}
