package experiments

import java.io._

import com.typesafe.scalalogging.Logger
import sgrub.chain.ChainDataOwner
import sgrub.inmemory.InMemoryStorageProvider

import scala.collection.mutable

class ExperimentPutSingleBatch(bytes: Array[Int], replicate: Boolean) {
  private val log = Logger(getClass.getName)

  // Create a new contract.
  private val newContracts = ExperimentTools.deployContracts()
  private val smAddress = newContracts._1

  // Objects
  val SP = new InMemoryStorageProvider
  val DO = new ChainDataOwner(SP, replicate, ExperimentTools.createGasLogCallback(log, "ChainDataOwnerLogCallback", callback), smAddress = smAddress)

  // Looping
  private var currentKey = -1L
  private var running = true
  private var firstInput = true

  // Results
  private var results = List(): List[ExperimentResult]

  private def callback(gasUsed: BigInt): Unit = {
    // Store the results.
    if(currentKey >= 0){
      results = results :+ new ExperimentResult(currentKey, bytes(currentKey.toInt), gasUsed)
      storeExperiment()
    }

    // We continue with the next experiment.
    firstInput = false
    currentKey += 1
    if(currentKey >= bytes.length){
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
    val pw = new PrintWriter(new File(s"results/experiment-putsinglebatch-$replicate-${bytes.mkString("_")}.csv" ))
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
    val byteSize :Int = if (firstInput) 1 else bytes(currentKey.toInt)
    result((currentKey + 2) % Long.MaxValue) = Array.fill(byteSize)((scala.util.Random.nextInt(90-56) + 56).toByte)

    // Return the result.
    result.toMap
  }

  private class ExperimentResult(step: Long, bytes: Int, gasUsed: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$step;$bytes;$gasUsed\n")
    }
  }
}