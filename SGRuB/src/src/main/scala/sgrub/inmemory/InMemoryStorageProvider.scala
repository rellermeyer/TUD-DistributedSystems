package sgrub.inmemory

import com.google.common.primitives.Longs
import scorex.crypto.authds.avltree.batch._
import scorex.crypto.authds.{ADDigest, ADKey, ADValue, SerializedAdProof}
import sgrub.contracts._

import scala.util.Success

/**
 * Represents the off-chain data storage, stores data in-memory
 */
class InMemoryStorageProvider extends StorageProvider {
  private val prover = new BatchAVLProver[DigestType, HashFunction](keyLength = KeyLength, valueLengthOpt = None)(hf)
  override val initialDigest: ADDigest = prover.digest

  override def gPuts(kvs: Map[Long, Array[Byte]]): (ADDigest, SerializedAdProof) = {
    val ops = kvs.map(kv => InsertOrUpdate(ADKey @@ Longs.toByteArray(kv._1), ADValue @@ kv._2))
    ops.foreach(prover.performOneOperation)
    (prover.digest, prover.generateProof())
  }

  override def request(key: Long, callback: SerializedAdProof => Unit): Unit = {
    prover.performOneOperation(Lookup(ADKey @@ Longs.toByteArray(key))) match {
      case Success(some) => some match {
        case Some(result) => callback(prover.generateProof())
        case _ => //..
      }
      case _ => // Request it
    }
  }
}
