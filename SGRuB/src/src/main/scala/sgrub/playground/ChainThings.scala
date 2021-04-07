package sgrub.playground

import com.google.common.primitives.Longs
import com.typesafe.scalalogging.Logger
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.http.HttpService
import org.web3j.tx.{Contract, RawTransactionManager}
import org.web3j.tx.gas.StaticGasProvider
import sgrub.chain.{ChainDataOwner, ChainDataUser, StorageProviderChainListener}
import sgrub.inmemory.InMemoryStorageProvider
import sgrub.smartcontracts.generated.StorageManager.RequestEventResponse
import sgrub.smartcontracts.generated.{Storage, StorageManager, StorageProviderEventManager}
import java.math.BigInteger
import java.security.InvalidParameterException

import sgrub.chain.ChainTools.logGasUsage
import sgrub.config

import scala.io.StdIn
import scala.util.{Failure, Success, Try}

object ChainThings {
  private val log = Logger(getClass.getName)
  private val web3 = Web3j.build(new HttpService("http://localhost:8101"))
  private val gasProvider = new StaticGasProvider(new BigInteger("1000000000"), new BigInteger("8000000"))
  private val credentials = WalletUtils.loadCredentials("password", config.getString("sgrub.do.keyLocation"))
  private val transactionManager = new RawTransactionManager(web3, credentials, 15)
  private var _sp: Option[StorageProviderEventManager] = None
  private var _spAddress: Option[String] = None
  private var _sm: Option[StorageManager] = None
  private var _smAddress: Option[String] = None
  def SP: Option[StorageProviderEventManager] = _sp
  def SM: Option[StorageManager] = _sm
  def SPAddress: Option[String] = _spAddress
  def SMAddress: Option[String] = _smAddress

  def deploy(): (Try[StorageManager], Try[StorageProviderEventManager]) = {
    log.info("Deploying contracts...")
    val tryContractSM = Try(StorageManager.deploy(web3, transactionManager, gasProvider).send())
    val tryContractSP = Try(StorageProviderEventManager.deploy(web3, transactionManager, gasProvider).send())
    if (tryContractSM.isSuccess) {
      val contract = tryContractSM.get
      val receipt = contract.getTransactionReceipt
      log.info(s"SM Transaction receipt: $receipt")
      receipt.ifPresent(r => log.info(s"SM deploy gas used: ${r.getGasUsed}"))
      log.info(s"SM Contract address: ${contract.getContractAddress}")
      if (!contract.isValid) {
        return (Failure(new InvalidParameterException("SM Contract was invalid")), tryContractSP)
      }

      _sm = Some(contract)
      _smAddress = Some(contract.getContractAddress)
    }
    if (tryContractSP.isSuccess) {
      val contract = tryContractSP.get
      val receipt = contract.getTransactionReceipt
      log.info(s"SP Transaction receipt: $receipt")
      receipt.ifPresent(r => log.info(s"SP deploy gas used: ${r.getGasUsed}"))
      log.info(s"SP Contract address: ${contract.getContractAddress}")
      if (!contract.isValid) {
        return (tryContractSM, Failure(new InvalidParameterException("SP Contract was invalid")))
      }

      _sp = Some(contract)
      _spAddress = Some(contract.getContractAddress)
    }
    (tryContractSM, tryContractSP)
  }

  def connect_to_sp(storageAddress: Option[String] = _spAddress): Try[StorageProviderEventManager] = {
    SP match {
      case Some(storageExists) => Success(storageExists)
      case _ => storageAddress match {
        case Some(addressExists) => Try(StorageProviderEventManager.load(addressExists, web3, transactionManager, gasProvider))
        case _ => Failure(new InvalidParameterException("Storage Provider has not been deployed yet"))
      }
    }
  }

  def connect_to_sm(storageAddress: Option[String] = _spAddress): Try[StorageManager] = {
    SM match {
      case Some(storageExists) => Success(storageExists)
      case _ => storageAddress match {
        case Some(addressExists) => Try(StorageManager.load(addressExists, web3, transactionManager, gasProvider))
        case _ => Failure(new InvalidParameterException("Storage Provider has not been deployed yet"))
      }
    }
  }

  def tryChain(tSM: Try[StorageManager], tSP: Try[StorageProviderEventManager]): Unit = {
    tSM match {
      case Success(sm) => {
        tSP match {
          case Success(sp) => {
            val ISP = new InMemoryStorageProvider
            println("Make DO replicate? (y/n)")
            val replicate = StdIn.readBoolean()
            val DO = new ChainDataOwner(ISP, replicate)
            val DU = new ChainDataUser()
            val someNewData = Map[Long, Array[Byte]](
              1L -> "Some Arbitrary Data".getBytes(),
              2L -> "Some More Arbitrary Data".getBytes(),
              3L -> "Hi".getBytes(),
              4L -> "Hello".getBytes(),
            )
            DO.gPuts(someNewData)
            DU.gGet(1L, (key, value) => {
              log.info(s"Value returned. Key: $key, Value: ${new String(value)}")
            })
            val listener = new StorageProviderChainListener(ISP)
            listener.listen()
          }
          case _ => log.error("Deploying SP failed")
        }
      }
      case _ => log.error("Deploying SM failed")
    }

  }

  def userInputThings(): Unit = {
    println(
      "\n" +
        "\n================================" +
        "\nSMART CONTRACT TEST (Full on-chain test)" +
        "\n================================")
    println("Deploy new contract? (y/n)")
    val deployInput = StdIn.readBoolean()
    if (deployInput) {
      val (trySM, trySP) = deploy()
      tryChain(trySM, trySP)
    } else {
      val spAddress = StdIn.readLine("SP Address?\n")
      val smAddress = StdIn.readLine("SM Address?\n")
      tryChain(connect_to_sm(Some(smAddress)), connect_to_sp(Some(spAddress)))
    }
  }
}
