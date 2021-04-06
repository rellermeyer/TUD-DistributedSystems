package experiments

import java.io._

import com.typesafe.scalalogging.Logger
import sgrub.chain.ChainDataOwner
import sgrub.inmemory.InMemoryStorageProvider

import scala.collection.mutable

class ExperimentPutBatch(sizes: Array[Int], amount: Array[Int], replicate: Boolean) {
  private val log = Logger(getClass.getName)

  // Create a new contract.
  private val newContracts = ExperimentTools.deployContracts()
  private val smAddress = newContracts._1

  // Objects
  val SP = new InMemoryStorageProvider
  val DO = new ChainDataOwner(SP, replicate, ExperimentTools.createGasLogCallback(log, "ChainDataOwnerLogCallback", callback), smAddress = smAddress)

  // Looping
  private var currentKey = 1
  private var running = true
  private var firstInput = true
  private var currentSize = 0

  // The amount iteration
  private var currentAmount = 0
  private var currentTotalGas = 0: BigInt

  // Results
  private var results = List(): List[ExperimentResult]

  private def callback(gasUsed: BigInt): Unit = {
    // Store the results.
    if(!firstInput){
      currentTotalGas += gasUsed
      currentAmount += 1

      if(currentAmount >= amount(currentSize)){
        results = results :+ new ExperimentResult(currentSize, sizes(currentSize), amount(currentSize), currentTotalGas)
        storeExperiment()

        currentSize += 1
        currentTotalGas = 0
        currentAmount = 0
      }
    } else {
      firstInput = false
    }

    // We continue with the next experiment.
    if(currentSize >= sizes.length){
      // Stop the loop.
      running = false

    }

    // Continue with the experiments.
    else {
      DO.gPuts(createBatch())
    }
  }

  def storeExperiment(): Unit = {
    // Write results to file.
    val pw = new PrintWriter(new File(s"results/experiment-putbatch-$replicate-${sizes.mkString("_")}-${amount.mkString("_")}.csv" ))
    results.foreach((element: ExperimentResult) => {
      element.write(pw)
    })
    pw.close()
  }

  def startExperiment(): Unit = {
    DO.gPuts(createBatch())

    // Keep the thread active.
    while(running){}
  }

  def createBatch(): Map[Long, Array[Byte]] = {
    val result = mutable.Map.empty[Long, Array[Byte]]

    // Create the result
    for(batches <- 0 until sizes(currentSize)) {
      result(currentKey % Long.MaxValue) = Array.fill(1)((scala.util.Random.nextInt(90 - 56) + 56).toByte)
      currentKey += 1
    }

    // Return the result.
    result.toMap
  }

  private class ExperimentResult(step: Int, size: Int,  amount: Int, gasUsed: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$step;$size;$amount;$gasUsed\n")
    }
  }
}