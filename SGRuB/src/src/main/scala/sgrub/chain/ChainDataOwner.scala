package sgrub.chain

import java.math.BigInteger

import com.google.common.primitives.Longs
import com.typesafe.scalalogging.Logger
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.gas.StaticGasProvider
import scorex.crypto.authds.{ADDigest, ADKey, ADValue, EmptyByteArray}
import scorex.crypto.authds.avltree.batch.{BatchAVLVerifier, InsertOrUpdate}
import sgrub.config
import sgrub.contracts.{DataOwner, DigestType, HashFunction, KeyLength, StorageProvider, hf}
import sgrub.smartcontracts.generated.StorageManager

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

class ChainDataOwner(
  sp: StorageProvider,
  shouldReplicate: Boolean = false,
  logGasUsage: (String, () => TransactionReceipt) => Try[TransactionReceipt] = ChainTools.logGasUsage,
  smAddress: String = config.getString("sgrub.smContractAddress"),
) extends DataOwner {
  private val log = Logger(getClass.getName)
  private val credentials = WalletUtils.loadCredentials(config.getString("sgrub.do.password"), config.getString("sgrub.do.keyLocation"))
  private val transactionManager = new RawTransactionManager(web3, credentials, config.getInt("sgrub.chainId"))

  private val storageManager: StorageManager = Try(StorageManager.load(smAddress, web3, transactionManager, gasProvider)) match {
    case Success(sm) => sm
    case Failure(exception) => {
      log.error(s"Unable to load StorageManager with address $smAddress, exception: $exception")
      sys.exit(1)
    }
  }
  private def storageProvider: StorageProvider = sp
  private var _latestDigest: ADDigest = config.getString("sgrub.do.startingDigest") match {
    case digest if !digest.isEmpty => ADDigest @@ Hex.decode(digest)
    case _ => storageProvider.initialDigest
  }
//  private var _latestDigest: ADDigest = storageProvider.initialDigest
  override def latestDigest: ADDigest = _latestDigest

  /**
   * Runs the ADS protocol with [[storageProvider]]
   *
   * @param kvs The key-value pairs to be updated
   * @return True when the KVs were updated successfully and securely, otherwise returns False
   */
  override def gPuts(kvs: Map[Long, Array[Byte]]): Boolean = {
    log.info(s"gPuts: ${kvs.map(kv => (kv._1, new String(kv._2)))}")
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
        log.info(s"Updating digest, new digest: ${Hex.toHexString(receivedDigest)}")
        if (shouldReplicate) {
          logGasUsage("Update digest and replicate",
            () => {
              val kvsOrdered = mutable.LinkedHashMap(kvs.map(kv => (Longs.toByteArray(kv._1), kv._2)).toSeq:_*)
              storageManager.update(kvsOrdered.keySet.toList.asJava, kvsOrdered.values.toList.asJava, _latestDigest).send()
            }) match {
            case Success(_) => true
            case Failure(exception) => {
              log.error(s"Update digest and replicate failed: $exception")
              false
            }
          }
        } else {
          logGasUsage("Update digest",
            () => storageManager.updateDigestOnly(_latestDigest).send()) match {
            case Success(_) => true
            case Failure(exception) => {
              log.error(s"Update digest failed: $exception")
              false
            }
          }
        }
      }
      case _ => false
    }
  }
}
