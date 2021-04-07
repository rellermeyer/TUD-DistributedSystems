package sgrub.chain

import com.typesafe.scalalogging.Logger
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.tx.RawTransactionManager
import sgrub.config
import sgrub.smartcontracts.generated.{StorageManager, StorageProviderEventManager}

import scala.util.{Failure, Success, Try}

object ChainTools {
  private val log = Logger(getClass.getName)

  def logGasUsage(functionName: String, function: () => TransactionReceipt): Try[TransactionReceipt] = {
    val result = Try(function())
    result match {
      case Success(receipt) => {
        log.info(s"'$functionName' succeeded, gas used: ${receipt.getGasUsed}")
        result
      }
      case Failure(exception) => {
        log.error(s"'$functionName' failed, unable to measure gas. Exception: $exception")
        result
      }
    }
  }

  def deployContracts(): Unit = {
    val doCredentials = WalletUtils.loadCredentials(config.getString("sgrub.do.password"), config.getString("sgrub.do.keyLocation"))
    val doTransactionManager = new RawTransactionManager(web3, doCredentials, config.getInt("sgrub.chainId"))
    val spCredentials = WalletUtils.loadCredentials(config.getString("sgrub.sp.password"), config.getString("sgrub.sp.keyLocation"))
    val spTransactionManager = new RawTransactionManager(web3, spCredentials, config.getInt("sgrub.chainId"))
    log.info("Deploying contracts...")
    Try(StorageManager.deploy(web3, doTransactionManager, gasProvider).send()) match {
      case Success(contract) => {
        val receipt = contract.getTransactionReceipt
        log.info(s"SM Transaction receipt: $receipt")
        receipt.ifPresent(r => log.info(s"SM deploy gas used: ${r.getGasUsed}"))
        log.info(s"SM Contract address (put this in the config file): ${contract.getContractAddress}")
        if (!contract.isValid) {
          log.error("SM contract was invalid")
        }
      }
      case Failure(exception) => {
        log.error(s"SM contract deployment failed with: $exception")
      }
    }
    Try(StorageProviderEventManager.deploy(web3, spTransactionManager, gasProvider).send()) match {
      case Success(contract) => {
        val receipt = contract.getTransactionReceipt
        log.info(s"SP Transaction receipt: $receipt")
        receipt.ifPresent(r => log.info(s"SP deploy gas used: ${r.getGasUsed}"))
        log.info(s"SP Contract address (put this in the config file): ${contract.getContractAddress}")
        if (!contract.isValid) {
          log.error("SP contract was invalid")
        }
      }
      case Failure(exception) => {
        log.error(s"SP contract deployment failed with: $exception")
      }
    }
  }
}
