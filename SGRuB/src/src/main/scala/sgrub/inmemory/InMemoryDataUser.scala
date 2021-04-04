package sgrub.inmemory

import com.google.common.primitives.Longs
import scorex.crypto.authds.avltree.batch.{BatchAVLVerifier, Lookup}
import scorex.crypto.authds.{ADDigest, ADKey, SerializedAdProof}
import sgrub.contracts._

import scala.collection.mutable
import scala.util.{Failure, Success}

/**
 * Represents the Data User, stores data in memory and has a direct connection to the SP object, instead of via the blockchain
 * @param sp The storage provider
 */
class InMemoryDataUser(
  sp: StorageProvider
) extends DataUser {
  private val kvReplicas = mutable.Map.empty[Long, Array[Byte]]
  private var _latestDigest: ADDigest = ADDigest @@ Array.empty[Byte]

  def latestDigest: ADDigest = _latestDigest

  /**
   * Called by the DO to update the digest and any key-value pairs to replicate/delete
   * @param kvs Key-value pairs to replicate/delete
   * @param newDigest Latest digest
   */
  def update(kvs: Map[Long, Array[Byte]], newDigest: ADDigest): Unit = {
    // Needs logic to determine whether to replicate/delete KVs based on key, ignoring that part for now
    kvs.foreach(kv => kvReplicas(kv._1) = kv._2)
    _latestDigest = newDigest
  }

  override def gGet(key: Long, callback: (Long, Array[Byte]) => Unit): Unit = {
    kvReplicas.get(key) match {
      case Some(value) => callback(key, value)
      case _ => request(key, (replicate, proof) => deliver(key, replicate, proof, callback))
    }
  }

  /**
   * Function (indirectly) called by the Storage Provider to return a key-value pair that wasn't replicated before
   * @param key Key which was called originally in [[gGet]]
   * @param replicate Instruction to replicate the value or not
   * @param proof Proof from the Storage Provider
   * @param callback The original query on the key-value pair
   */
  def deliver(key: Long, replicate: Boolean, proof: SerializedAdProof, callback: (Long, Array[Byte]) => Unit): Unit = {

    val verifier = new BatchAVLVerifier[DigestType, HashFunction](
      latestDigest,
      proof,
      keyLength = KeyLength,
      valueLengthOpt = None,
      maxNumOperations = Some(1),
      maxDeletes = Some(0)
    )(hf)

    verifier.performOneOperation(Lookup(ADKey @@ Longs.toByteArray(key))) match {
      case Success(successResult) => successResult match {
        case Some(existResult) => {
          if (replicate) kvReplicas(key) = existResult
          callback(key, existResult)
        }
        case _ => println(s"Fail. No value for key: $key")
      }
      case Failure(exception) => println(s"Fail. $exception")
    }
  }

  /**
   * Sends an event to be picked up by the SP to deliver a non-replicated key-value pair
   * @param key
   * @param deliver
   */
  def request(key: Long, deliver: (Boolean, SerializedAdProof) => Unit): Unit = {
    // This would normally be done over the blockchain, for now we use a direct connection with the SP
    sp.request(key, proof => deliver(false, proof))
  }
}
