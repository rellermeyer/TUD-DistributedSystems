package sgrub.inmemory

import com.google.common.primitives.Longs
import scorex.crypto.authds.avltree.batch._
import scorex.crypto.authds.{ADDigest, ADKey, ADValue, SerializedAdProof}
import sgrub.contracts._

import scala.util.Success

/**
 * Represents a malicious off-chain data storage provider, modifies data entered into [[gPuts]]
 */
class MaliciousStorageProvider extends StorageProvider {
  private val prover = new BatchAVLProver[DigestType, HashFunction](keyLength = KeyLength, valueLengthOpt = None)(hf)
  override val initialDigest: ADDigest = prover.digest

  override def gPuts(kvs: Map[Long, Array[Byte]]): (ADDigest, SerializedAdProof) = {
    val ops = kvs.map(kv => InsertOrUpdate(ADKey @@ Longs.toByteArray(kv._1),
      ADValue @@ "Haxxorz".getBytes()))
    ops.foreach(prover.performOneOperation)
    (prover.digest, prover.generateProof())
  }

  override def request(key: Long, callback: SerializedAdProof => Unit): Unit = {
    val lookup = Lookup(ADKey @@ Longs.toByteArray(key))
    prover.performOneOperation(lookup) match {
      case Success(_) => callback(prover.generateProof())
      case _ => // This shouldn't happen?
    }
  }
}
