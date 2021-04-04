package experiments

import java.io.{File, PrintWriter}

import com.typesafe.scalalogging.Logger
import sgrub.chain.{ChainDataOwner, ChainDataUser}
import sgrub.experiments.BatchCreator
import sgrub.inmemory.InMemoryStorageProvider

class ExperimentGet(sizes: Array[Int], samples: Int, replicate: Boolean) {
  private val log = Logger(getClass.getName)

  // Create a new contract.
  private val (smAddress, spAddress) = ExperimentTools.deployContracts()

  // Objects.
  private val DU = new ChainDataUser(ExperimentTools.createGasLogCallback(log, "ChainDataUserLogCallback", getCallBack), smAddress = smAddress, spAddress = spAddress)

  // The loop.
  private var firstRun = true
  private var running = true
  private var currentKey = 1
  private var currentSize = 0

  // The sample
  private var currentGasCost = 0 :BigInt
  private var currentSample = 0

  // The results
  private var results = List() : List[ExperimentResult]


  def getCallBack(gasCost: BigInt): Unit = {

    if(!firstRun){

      currentGasCost += gasCost
      currentSample += 1

      if(currentSample >= samples){
        // Keep track of the result.
        results = results :+ new ExperimentResult(currentSize, sizes(currentSize), currentGasCost / samples)

        // Store the results after each update
        val pw = new PrintWriter(new File(s"results/experiment-get-$samples-$replicate-${sizes.mkString("_")}.csv" ))
        results.foreach((element: ExperimentResult) => {
          element.write(pw)
        })
        pw.close()

        // Next get.
        currentKey += 1
        currentSize += 1
        currentSample = 0
        currentGasCost = 0
      }
    } else {
      firstRun = false
    }

    // Check if inbounds.
    if(currentSize > sizes.length){

      // Stop the loop.
      running = false
    }

    // We are still running, so perform next get.
    else {
      DU.gGet(currentKey, (_, _) => {})
    }
  }


  def startExperiment(): Unit = {
    val SP = new InMemoryStorageProvider
    val DO = new ChainDataOwner(SP, replicate, ExperimentTools.createGasLogCallback(log, "ChainDataOwnerLogCallback", (_: BigInt) => {
      // Call the get.
      DU.gGet(currentKey, (_, _) => {})
    }), smAddress=smAddress)

    DO.gPuts(BatchCreator.createSizedBatch(sizes))

    // Keep the code running.
    while(running){}
  }

  class ExperimentResult(index: Int, size: Int, gasCost: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$index;$size;$gasCost\n")
    }
  }
}