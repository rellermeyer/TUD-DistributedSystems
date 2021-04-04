package sgrub.playground

import java.math.BigInteger
import java.security.InvalidParameterException
import java.util

import io.reactivex.subscribers.DisposableSubscriber
import org.web3j.abi.datatypes.{Address, Event, Uint}
import org.web3j.abi.{EventEncoder, TypeReference}
import org.web3j.crypto.WalletUtils
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.http.HttpService
import org.web3j.tx.gas.StaticGasProvider
import org.web3j.tx.{Contract, RawTransactionManager}
import sgrub.smartcontracts.generated.{Storage, StorageManager}

import scala.io.StdIn
import scala.util.{Failure, Success, Try}

class SmartcontractThings(gethPath: String) {
  private val web3 = Web3j.build(new HttpService("http://localhost:8101"))
  private val gasProvider = new StaticGasProvider(new BigInteger("1000000000"), new BigInteger("8000000"))
  private val credentials = WalletUtils.loadCredentials("password", s"$gethPath/node01/keystore/UTC--2021-03-14T10-55-22.116143363Z--f90b82d1f4466e7e83740cad7c29f4576334eeb4")
  private val transactionManager = new RawTransactionManager(web3, credentials, 15)
  private var _storage: Option[Contract] = None

  def storage: Option[Contract] = _storage

  private var _address: Option[String] = None

  def address: Option[String] = _address

  def deploy(s: String): Try[Any] = {
    println("Deploying contract...")
    val tryContract = s match {
      case "Storage" => {
        println("Deploying Storage contract...")
        Try(Storage.deploy(web3, transactionManager, gasProvider).send())
      }
      case "StorageProvider" => {
        println("Deploying StorageProvider contract...")
        Try(StorageManager.deploy(web3, transactionManager, gasProvider).send())
      }
    }
    if (tryContract.isSuccess) {
      val contract = tryContract.get
      println(s"Transaction receipt: ${contract.getTransactionReceipt}")
      println(s"Contract address: ${contract.getContractAddress}")
      if (!contract.isValid) {
        return Failure(new InvalidParameterException("Contract was invalid"))
      }

      _storage = Some(contract)
      _address = Some(contract.getContractAddress)
    }
    tryContract
  }

  def connect_to_storage(storageAddress: Option[String] = address, s: String = "Storage"): Try[Contract] = {
    storage match {
      case Some(storageExists) => Success(storageExists)
      case _ => storageAddress match {
        case Some(addressExists) => {
          s match {
            case "Storage" => Try(Storage.load(addressExists, web3, transactionManager, gasProvider))
            case "StorageProvider" => Try(StorageManager.load(addressExists, web3, transactionManager, gasProvider))
          }
        }
        case _ => Failure(new InvalidParameterException("Storage has not been deployed yet"))
      }
    }
  }

  def tryCall(tryStorage: Try[Storage]): Unit = {
    tryStorage match {
      case Success(storage) => {
        println("Number to store?")
        val toStore = BigInteger.valueOf(StdIn.readInt())
        println(s"Storing $toStore")
        storage.store(toStore).send()
        println("Stored. Retrieving...")
        println(s"Retrieved: ${storage.retrieve().send()}")
      }
      case Failure(ex) => println(s"Failed with: $ex")
    }
  }

//  def tryCall2(tryStorage: Try[StorageManager]): Unit = {
//    tryStorage match {
//      case Success(storage) => {
//        println("key to store?")
//        val toStore = BigInteger.valueOf(StdIn.readInt())
//        val bytes = "test".getBytes()
//        println(s"Storing test bytes at $toStore")
//        storage.update(toStore, bytes, bytes).send()
//        println("Stored")
//      }
//      case Failure(ex) => println(s"Failed with: $ex")
//    }
//  }
//
//  def tryCall3(tryStorage: Try[StorageManager]): Unit = {
//    tryStorage match {
//      case Success(storage) => {
//        println("key to get?")
//        val toGet = BigInteger.valueOf(StdIn.readInt())
//        println(s"Getting bytes at $toGet")
//        storage.gGet(toGet).send()
//        println("Request sent")
//        val receipt = _storage.get.getTransactionReceipt.get()
//        println("Printing transaction event log: ")
//        storage.getRequestEvents(receipt).forEach(e => {
//          val key = e.key
//          val sender = e.sender
//          println(s"found request for $key from $sender")
//        })
//      }
//      case Failure(ex) => println(s"Failed with: $ex")
//    }
//  }

  def userInputThings(): Unit = {
    println(
      "\n" +
        "\n================================" +
        "\nSMART CONTRACT TEST (Basic Storage)" +
        "\n================================")
    println("Deploy new contract? (y/n)")
    val deployInput = StdIn.readBoolean()
    if (deployInput) {
      StdIn.readLine("Name of generated class? (Storage/StorageProvider)\n") match {
        case "Storage" => tryCall(deploy("Storage").asInstanceOf[Try[Storage]])
        //case "StorageProvider" => tryCall2(deploy("StorageProvider").asInstanceOf[Try[StorageManager]])
      }
    } else {
      val generatedClass = StdIn.readLine("Name of generated class? (Storage/StorageProvider)\n")
      val inputAddress = StdIn.readLine("Please enter the storage contract address:\n")
      //tryCall3(connect_to_storage(Some(inputAddress), s = generatedClass).asInstanceOf[Try[StorageManager]])
    }
  }

  def startListener(): Unit = {
    new EventListener().listen()
  }

  private class EventListener() {
    // Definition of event: request(uint indexed key, address indexed sender);
    val event = new Event(
      "request",
      util.Arrays.asList[TypeReference[_]](
        new TypeReference[Uint](true) {},
        new TypeReference[Address](true) {}));

    //need get the encoding for the event of interest to filter from EVM log
    val eventHash = EventEncoder.encode(event);

    //filter on address, blocks, and event definition
    val filter = new EthFilter(
      DefaultBlockParameterName.EARLIEST, //search from block (maybe change to latest?)
      DefaultBlockParameterName.LATEST, // to block
      _address.get // smart contract that emits event
    ).addSingleTopic(eventHash); //filter on event definition

    // subscriber logic for handling incoming event logs
    val subscriber = new DisposableSubscriber[Any]() {
      override def onNext(t: Any): Unit = println("SUCCESS:  " + t.toString)

      override def onError(t: Throwable): Unit = t.printStackTrace()

      override def onComplete(): Unit = println("stopped listening")
    }

    def listen(): Unit = {
      println("listening")
      web3.ethLogFlowable(filter).subscribeWith(subscriber);
    }

    def stopListening(): Unit = {
      subscriber.dispose()
    }
  }

}
