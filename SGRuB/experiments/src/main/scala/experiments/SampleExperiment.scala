package experiments

import org.web3j.protocol.core.methods.response.TransactionReceipt
import sgrub.chain.{ChainDataOwner, ChainDataUser, StorageProviderChainListener}
import sgrub.inmemory.InMemoryStorageProvider

import scala.util.{Failure, Success, Try}

object SampleExperiment {
  def customGasLog(functionName: String, function: () => TransactionReceipt): Try[TransactionReceipt] = {
    val result = Try(function())
    result match {
      case Success(receipt) => {
        println(s"'$functionName' succeeded, gas used: ${receipt.getGasUsed}")
        result
      }
      case Failure(exception) => {
        println(s"'$functionName' failed, unable to measure gas. Exception: $exception")
        result
      }
    }
  }

  def veryCustomGasLog(extraParam: String, functionName: String, function: () => TransactionReceipt): Try[TransactionReceipt] = {
    val result = Try(function())
    println(s"I have an extra parameter! $extraParam")
    result match {
      case Success(receipt) => {
        println(s"'$functionName' succeeded, gas used: ${receipt.getGasUsed}")
        result
      }
      case Failure(exception) => {
        println(s"'$functionName' failed, unable to measure gas. Exception: $exception")
        result
      }
    }
  }

  def run(): Unit = {
    // Deploy clean contracts
    val (smAddress, spAddress) = ExperimentTools.deployContracts()

    // Create DO, SP and DU
    val SP = new InMemoryStorageProvider
    val DO = new ChainDataOwner(
      sp = SP,
      shouldReplicate = false,
      logGasUsage = customGasLog,
      smAddress = smAddress)
    val listener = new StorageProviderChainListener(
      storageProvider = SP,
      logGasUsage = (functionName, function) => veryCustomGasLog("Something else", functionName, function),
      smAddress = smAddress,
      spAddress = spAddress
    ).listen()
    val DU = new ChainDataUser(
      logGasUsage = customGasLog,
      smAddress = smAddress,
      spAddress = spAddress
    )

    // Add some things
    DO.gPuts(Map(
      1L -> "Some Arbitrary Data".getBytes(),
      2L -> "Some More Arbitrary Data".getBytes(),
      3L -> "Hi".getBytes(),
      4L -> "Hello".getBytes(),
    ))

    // Try to get one
    DU.gGet(1L, (key, value) => {
      println(s"Successfully got key: $key, value: ${new String(value)}")
      listener.dispose()
    })
  }
}
