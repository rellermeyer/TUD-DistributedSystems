package experiments

import com.typesafe.scalalogging.Logger
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import sgrub.chain.{gasProvider, web3}
import sgrub.smartcontracts.generated.{StorageManager, StorageProviderEventManager}

import scala.util.{Failure, Success, Try}
object ExperimentTools {
  private val log = Logger(getClass.getName)

  def deployContracts(): (String, String) ={
    log.info("Deploying contracts...")
    val doCredentials = WalletUtils.loadCredentials(config.getString("sgrub.do.password"), config.getString("sgrub.do.keyLocation"))
    val doTransactionManager = new RawTransactionManager(web3, doCredentials, config.getInt("sgrub.chainId"))
    val spCredentials = WalletUtils.loadCredentials(config.getString("sgrub.sp.password"), config.getString("sgrub.sp.keyLocation"))
    val spTransactionManager = new RawTransactionManager(web3, spCredentials, config.getInt("sgrub.chainId"))
    val sm = StorageManager.deploy(web3, doTransactionManager, gasProvider).send()
    val sp = StorageProviderEventManager.deploy(web3, spTransactionManager, gasProvider).send()

    log.info(s"Contracts deployed sm=${sm.getContractAddress} sp=${sp.getContractAddress}")
    (sm.getContractAddress, sp.getContractAddress)
  }

  def createGasLogCallback(logger: Logger, name: String, callback: BigInt => Unit) = (functionName: String, function: () => TransactionReceipt) => {
    val result = Try(function())
    result match {
      case Success(receipt) => {
        val gasCost = receipt.getGasUsed
        logger.info(s"'$name' succeeded, gas used: $gasCost")
        callback(gasCost)
        result
      }
      case Failure(exception) => {
        logger.error(s"'$name' failed, unable to measure gas. Exception: $exception")
        callback(-1)
        result
      }
    }
  }
}
