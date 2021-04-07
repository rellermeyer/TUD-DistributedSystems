package sgrub.chain

import com.google.common.primitives.Longs
import com.typesafe.scalalogging.Logger
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Predicate
import org.bouncycastle.util.encoders.Hex
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import scorex.crypto.authds.avltree.batch.{BatchAVLVerifier, Lookup}
import scorex.crypto.authds.{ADDigest, ADKey, SerializedAdProof}
import sgrub.config
import sgrub.contracts._
import sgrub.smartcontracts.generated.{StorageManager, StorageProviderEventManager}

import scala.concurrent.duration.SECONDS
import scala.util.{Failure, Success, Try}

class ChainDataUser(
  logGasUsage: (String, () => TransactionReceipt) => Try[TransactionReceipt] = ChainTools.logGasUsage,
  smAddress: String = config.getString("sgrub.smContractAddress"),
  spAddress: String = config.getString("sgrub.spContractAddress")
) extends DataUser {
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
  private val eventManager: StorageProviderEventManager = Try(StorageProviderEventManager.load(spAddress, web3, transactionManager, gasProvider)) match {
    case Success(sm) => sm
    case Failure(exception) => {
      log.error(s"Unable to load StorageProviderEventManager with address $spAddress, exception: $exception")
      sys.exit(1)
    }
  }

  override def gGet(key: Long, callback: (Long, Array[Byte]) => Unit): Unit = {
    log.info("Starting SM and SP deliver subscriptions")
    var smSubscription: Option[Disposable] = None
    var spSubscription: Option[Disposable] = None
    smSubscription = Some(storageManager.deliverEventFlowable(
      DefaultBlockParameterName.LATEST,
      DefaultBlockParameterName.LATEST)
      .timeout(config.getInt("sgrub.du.gGetTimeout"), SECONDS)
      .filter((event: StorageManager.DeliverEventResponse) =>
        Longs.fromByteArray(event.key) == key)
      .takeUntil(new Predicate[StorageManager.DeliverEventResponse] {
        override def test(t: StorageManager.DeliverEventResponse): Boolean = Longs.fromByteArray(t.key) == key
      })
      .doOnCancel(() => log.info("SM Deliver listener has been stopped."))
      .doOnComplete(() => log.info("SM Deliver listener has completed."))
      .doOnError(ex => log.error(s"SM Deliver listener had an error: $ex"))
      .subscribe((event: StorageManager.DeliverEventResponse) => {
        callback(key, event.value)
        spSubscription match {
          case Some(sub) => sub.dispose()
          case _ =>
        }
      }))
    spSubscription = Some(eventManager.deliverEventFlowable(
      DefaultBlockParameterName.LATEST,
      DefaultBlockParameterName.LATEST)
      .filter((event: StorageProviderEventManager.DeliverEventResponse) =>
        Longs.fromByteArray(event.key) == key)
      .takeUntil(new Predicate[StorageProviderEventManager.DeliverEventResponse] {
        override def test(t: StorageProviderEventManager.DeliverEventResponse): Boolean = verify(key, t.proof.asInstanceOf[SerializedAdProof], callback)
      })
      .timeout(config.getInt("sgrub.du.gGetTimeout"), SECONDS)
      .doOnCancel(() => log.info("SP Deliver listener has been stopped."))
      .doOnComplete(() => log.info("SP Deliver listener has completed."))
      .doOnError(ex => log.error(s"SP Deliver listener had an error: $ex"))
      .subscribe((_: StorageProviderEventManager.DeliverEventResponse) => {smSubscription match {
        case Some(sub) => sub.dispose()
        case _ =>
      }
      }))
    log.info(s"Attempting to gGet Key: $key")
    logGasUsage("gGet", () => storageManager.gGet(Longs.toByteArray(key)).send()) match {
      case Success(_) => // Do nothing
      case Failure(exception) => {
        log.error(s"gGet failed, stopping subscriptions. Exception: $exception")
        smSubscription match {
          case Some(sub) => sub.dispose()
          case _ =>
        }
        spSubscription match {
          case Some(sub) => sub.dispose()
          case _ =>
        }
      }
    }
  }

  private def verify(key: Long, proof: SerializedAdProof, callback: (Long, Array[Byte]) => Unit): Boolean = {
    log.info(s"Verifying for key: $key")
    log.info("Getting digest...")
    val latestDigest = ADDigest @@ storageManager.getDigest().send()
    log.info(s"Got digest: ${Hex.toHexString(latestDigest)}")
    if (latestDigest.length != DigestLength) {
      log.error(s"Digest length is incorrect, expected $DigestLength, got ${latestDigest.length}")
      return false
    }
    Try(new BatchAVLVerifier[DigestType, HashFunction](
      latestDigest,
      proof,
      keyLength = KeyLength,
      valueLengthOpt = None,
      maxNumOperations = Some(1),
      maxDeletes = Some(0)
    )(hf)) match {
      case Success(verifier) => verifier.performOneOperation(Lookup(ADKey @@ Longs.toByteArray(key))) match {
        case Success(successResult) => successResult match {
          case Some(existResult) => {
            callback(key, existResult)
            true
          }
          case _ => {
            log.error(s"Fail. No value for key: $key")
            false
          }
        }
        case Failure(exception) => {
          log.error(s"Fail. $exception")
          false
        }
      }
      case Failure(exception) => {
        log.error(s"Something went wrong during initialization of the verifier. $exception")
        false
      }
    }
  }
}
