package experiments

import java.io.{File, PrintWriter}

import com.typesafe.scalalogging.Logger
import io.reactivex.disposables.Disposable
import sgrub.chain.{ChainDataOwner, ChainDataUser, StorageProviderChainListener}
import sgrub.experiments.BatchCreator
import sgrub.inmemory.InMemoryStorageProvider

class ExperimentStaticBaselines(reads: Int, writes: Int, replicate: Boolean) {
  private val log = Logger(getClass.getName)

  // Create a new contract.
  private val (smAddress, spAddress) = ExperimentTools.deployContracts()

  // Objects.
  private val DU = new ChainDataUser(ExperimentTools.createGasLogCallback(log, "ChainDataUserLogCallback", if (!replicate) _ => {} else deliverCallBackNoGas), smAddress = smAddress, spAddress = spAddress)
  private var listener = null: Disposable

  // The loop.
  private var running = true
  private var currentReads = 0
  private var currentWrites = 0
  private var currentOperations = 0
  private var totalGasCost = 0 : BigInt

  // The results
  private var results = List() : List[ExperimentResult]


  def deliverCallBackNoGas(gasCost: BigInt): Unit = {
    deliverCallBack(0)
  }

  def deliverCallBack(gasCost: BigInt): Unit = {

    // Next get.
    currentOperations +=1
    currentReads += 1
    totalGasCost += gasCost

    println(s"Done with read $currentReads")

    // Keep track of the result.
    results = results :+ new ExperimentResult(currentOperations, currentReads, currentWrites, gasCost, totalGasCost)
    storeExperiment()

    // Check if inbounds.
    if(currentReads >= reads){

      // Stop the loop.
      running = false
    }

    // We are still running, so perform next get.
    else {
      DU.gGet(1, (_, _) => {})
    }
  }

  def storeExperiment(): Unit = {
    // Store the results.
    val pw = new PrintWriter(new File(s"results/experiment-baselines-$reads-$writes-$replicate.csv" ))
    results.foreach((element: ExperimentResult) => {
      element.write(pw)
    })
    pw.close()
  }

  def startExperiment(): Unit = {
    val SP = new InMemoryStorageProvider
    var DO: ChainDataOwner = null
    DO = new ChainDataOwner(SP, replicate, ExperimentTools.createGasLogCallback(log, "ChainDataOwnerLogCallback", (gasCost: BigInt) => {
      currentOperations += 1
      currentWrites += 1
      totalGasCost += gasCost

      // Save the result of the write.
      results = results :+ new ExperimentResult(currentOperations, currentReads, currentWrites, gasCost, totalGasCost)
      storeExperiment()

      // Check if we should keep writing or start getting.
      if(currentWrites < writes){
        DO.gPuts(BatchCreator.createSingleEntry(currentWrites + 1, 1))
      } else {
        DU.gGet(1 , (_, _) => {})
      }
    }), smAddress=smAddress)

    if(!replicate){
      listener = new StorageProviderChainListener(SP, ExperimentTools.createGasLogCallback(log, "StorageProviderChainListenerLogCallback", deliverCallBack), smAddress = smAddress, spAddress = spAddress).listen()
    }

    DO.gPuts(BatchCreator.createSingleEntry(currentWrites + 1, 1))

    // Keep the code running.
    while(running){}

    if(!replicate){
      listener.dispose()
    }
  }

  private class ExperimentResult(operations: Int, currReads: Int, currWrites: Int, gasUsed: BigInt, gasUsedTotal: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$operations;$currReads;$currWrites;$gasUsed;$gasUsedTotal\n")
    }
  }
}