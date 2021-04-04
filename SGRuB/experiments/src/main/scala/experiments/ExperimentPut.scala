package experiments

import java.io._

import com.typesafe.scalalogging.Logger
import sgrub.chain.ChainDataOwner
import sgrub.inmemory.InMemoryStorageProvider

import scala.collection.mutable

class ExperimentPut(maxBytes: Int, byteStepSize: Int, maxBatches: Int, batchStepSize: Int, rndDistribute: Boolean) {
  private val log = Logger(getClass.getName)

  // Create a new contract.
  private val (smAddress, _) = ExperimentTools.deployContracts()

  // Objects
  val SP = new InMemoryStorageProvider
  val DO = new ChainDataOwner(SP, true, ExperimentTools.createGasLogCallback(log, "ChainDataOwnerLogCallback", callback), smAddress = smAddress)

  // Looping
  private var currentBytes = 1
  private var currentBatches = 1
  private var currentKey = 1L
  private var running = true
  private var firstRun = true

  // Results
  private var results = List(): List[ExperimentResult]

  private def callback(gasUsed: BigInt): Unit = {
    // Store the results.
    if(!firstRun){
      results = results :+ new ExperimentResult(currentBytes * byteStepSize, currentBatches * batchStepSize, gasUsed)

      // Write results to file.
      val pw = new PrintWriter(new File(s"results/experiment-put-$maxBytes-$byteStepSize-$maxBatches-$batchStepSize-$rndDistribute.csv" ))
      results.foreach((element: ExperimentResult) => {
        element.write(pw)
      })
      pw.close()

      // We continue with the next experiment.
      currentBatches += 1
      if(currentBatches > maxBatches){
        currentBatches = 1
        currentBytes += 1
      }
    } else {
      firstRun = false
    }

    if(currentBytes > maxBytes) {
      // Stop the loop.
      running = false
    }

    // Continue with the experiments.
    else {
      startExperiment(currentBytes, currentBatches)
    }
  }

  def startExperiment(): Unit = {
    startExperiment(currentBytes, currentBatches)

    // Keep the thread active.
    while(running){}
  }

  private def startExperiment(bytes: Int, batches: Int): Unit = {
    val bytesTot = currentBytes * byteStepSize
    val batchesTot = currentBatches * batchStepSize

    log.info(s"Experiment $bytesTot bytes ${if (rndDistribute) "random" else "evenly"} distributed over $batchesTot batches.")

    // Inner experiment loop.
    DO.gPuts(createBatch(bytesTot, batchesTot))
  }

  def createBatch(bytes: Int, batches: Int): Map[Long, Array[Byte]] = {
    val result = mutable.Map.empty[Long, Array[Byte]]

    // The normal distributed experiment.
    if(!rndDistribute){
      // For each key we insert a batch.
      for(batch <- 0 until batches){
        // Fill the key with a random batch array of size bytes. The byte corresponds to a readable char.
        result(currentKey % Long.MaxValue) = Array.fill(bytes)((scala.util.Random.nextInt(90-56) + 56).toByte)
        currentKey += 1
      }
    }

    // The random distributed experiment.
    else {
      var bytesLeft = bytes * batches: Int
      for(batch <- 0 until batches - 1){

        // Retrieve random size and keep it inbounds.
        var currentBytes = scala.util.Random.nextInt(bytes * 2)
        if(currentBytes > bytesLeft){
          currentBytes = bytesLeft
        }

        result(currentKey % Long.MaxValue) = Array.fill(currentBytes)((scala.util.Random.nextInt(90-56) + 56).toByte)
        currentKey += 1
        bytesLeft -= currentBytes
      }
      // Add the remainder to the last key.
      result(currentKey % Long.MaxValue) = Array.fill(bytesLeft)((scala.util.Random.nextInt(90-56) + 56).toByte)
      currentKey += 1
    }


    // Return the result.
    result.toMap
  }

  private class ExperimentResult(bytes: Int, batches: Int, gasUsed: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$bytes;$batches;$gasUsed\n")
    }
  }
}