package sgrub.inmemory

import com.google.common.primitives.Longs
import scorex.crypto.authds.avltree.batch._
import scorex.crypto.authds.{ADDigest, ADKey, ADValue}
import sgrub.contracts._

import scala.collection.mutable

/**
 * Represents the Data Owner producing a stream of data updates, stores data in-memory
 * @param sp The off-chain storage provider, [[storageProvider]]
 */
class InMemoryDataOwner(
  sp: StorageProvider
) extends DataOwner {
  private var _latestDigest: ADDigest = storageProvider.initialDigest
  private val _users = mutable.Buffer.empty[InMemoryDataUser]
  override def latestDigest: ADDigest = _latestDigest

  private def storageProvider: StorageProvider = sp

  /**
   * Registers a data user to update with the latest digests and replication changes
   * @param user The DU to update
   */
  def register(user: InMemoryDataUser): Unit = {
    _users += user
  }

  override def gPuts(kvs: Map[Long, Array[Byte]]): Boolean = {
    // Internally, the gPuts...
    // First
    // > notifies the control plane on DO of the latest data updates
    // Then,
    // | for each data update:
    // > DO and SP jointly run the ADS protocol to securely update matching KV records

    val (receivedDigest, receivedProof) = storageProvider.gPuts(kvs)
    val ops = kvs.map(kv => InsertOrUpdate(ADKey @@ Longs.toByteArray(kv._1), ADValue @@ kv._2))
    val verifier = new BatchAVLVerifier[DigestType, HashFunction](
      _latestDigest,
      receivedProof,
      keyLength = KeyLength,
      valueLengthOpt = None,
      maxNumOperations = Some(ops.size),
      maxDeletes = Some(0)
    )(hf)

    ops.foreach(verifier.performOneOperation)

    verifier.digest match {
      case Some(digest) if digest.sameElements(receivedDigest) => {
        _latestDigest = receivedDigest
        // No replication logic yet
        _users.foreach(u => u.update(Map.empty, latestDigest))
        true
      }
      case _ => false
    }

    // | If:
    // | All KV records in this batch are in non-replicated state (NR)
    // | and there is no update on the replication state
    // > The DO sends only the digest of this batch to call the update() function in the storage-manager smart contract
    /// (Note that the blockchain node on the DO receiving the update() call would propagate it
    /// to other blockchain nodes.)
    // | If:
    // | There are any KV records with replicated state (R)
    // > They are included in the update() call.
    // | If:
    // | There is any state transition, either from R to NR or from NR to R
    // > Such transitions are included in the update call
  }
}
