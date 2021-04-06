package sgrub.experiments

import java.io.{File, PrintWriter}

import com.typesafe.scalalogging.Logger
import experiments.ExperimentTools
import io.reactivex.disposables.Disposable
import sgrub.chain.{ChainDataOwner, ChainDataUser, StorageProviderChainListener}
import sgrub.inmemory.InMemoryStorageProvider

class ExperimentDeliver(bytes: Array[Int], samples: Int) {
  private val log = Logger(getClass.getName)

  // Create a new contract.
  private val (smAddress, spAddress) = ExperimentTools.deployContracts()

  // Objects.
  private val DU = new ChainDataUser(smAddress=smAddress, spAddress=spAddress)
  private var listener = null: Disposable

  // The loop.
  private var running = true
  private var firstInput = true
  private var currentKey = 0

  // Sampling.
  private var currentSampleGasCost = 0: BigInt
  private var currentSample = 0

  // The results
  private var results = List() : List[ExperimentResult]

  def deliverCallBack(gasCost: BigInt): Unit ={


    // Increment the key.
    if(!firstInput){
      currentSampleGasCost += gasCost
      currentSample += 1

      if(currentSample >= samples){
        results = results :+ new ExperimentResult(currentKey, bytes(currentKey), currentSampleGasCost / samples)
        currentKey += 1

        // Reset sampling.
        currentSampleGasCost = 0
        currentSample = 0

        // Store the experiment per step, if something goes wrong.
        val pw = new PrintWriter(new File(s"results/experiment-deliver-$samples-${bytes.mkString("_")}.csv" ))
        results.foreach((element: ExperimentResult) => {
          element.write(pw)
        })
        pw.close()
      }
    } else {
      firstInput = false
    }

    // The loop is finished.
    if(currentKey >= bytes.length){

      // Dispose the listener.
      log.info("Dispose the listener")
      listener.dispose()

      // Stop the loop
      running = false
    }

    // Continue the loop by getting next value.
    else {
      DU.gGet(currentKey + 1, (_, _) => {})
    }
  }



  def startExperiment(): Unit = {
    // Initialise storage provider and data owner.
    val SP = new InMemoryStorageProvider
    val DO = new ChainDataOwner(SP, false, ExperimentTools.createGasLogCallback(log, "DataOwnerLogCallback", _ => {
      DU.gGet(1, (_, _) => {})
    }), smAddress = smAddress)

    // Create the listener and listen to delivers.
    listener = new StorageProviderChainListener(SP, ExperimentTools.createGasLogCallback(log, "StorageProviderChainListenerLogCallback", deliverCallBack), smAddress = smAddress, spAddress = spAddress).listen()

    // Put incremental length batch inside the contract.
    DO.gPuts(BatchCreator.createSizedBatch(bytes))


    // Keep the code running.
    while(running){}
  }


  class ExperimentResult(key: Int, bytes: Int, gasCost: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$key;$bytes;$gasCost\n")
    }
  }
}