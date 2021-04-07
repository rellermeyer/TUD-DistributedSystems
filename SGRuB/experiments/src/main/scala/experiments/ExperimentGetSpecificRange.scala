package experiments

import java.io.{File, PrintWriter}

import com.typesafe.scalalogging.Logger
import io.reactivex.disposables.Disposable
import sgrub.chain.{ChainDataOwner, ChainDataUser}
import sgrub.inmemory.InMemoryStorageProvider

import scala.collection.mutable

class ExperimentGetSpecificRange(bytes: Array[Int], replicate: Boolean) {
  private val log = Logger(getClass.getName)


  // Create a new contract.
  private val newContracts = ExperimentTools.deployContracts()
  private val smAddress = newContracts._1
  private val spAddress = newContracts._2

  // Objects.
  private val DU = new ChainDataUser(ExperimentTools.createGasLogCallback(log, "ChainDataUserLogCallback", getCallBack), smAddress = smAddress, spAddress = spAddress)
  private var listener = null: Disposable

  // The loop.
  private var running = true
  private var firstInput = true
  private var currentKey = 0

  // The results
  private var results = List() : List[ExperimentResult]


  def getCallBack(gasCost: BigInt): Unit = {
    // Increment the key.
    if(!firstInput){
      results = results :+ new ExperimentResult(currentKey, bytes(currentKey), gasCost)
      currentKey += 1

      // Store the experiment per step, if something goes wrong.
      val pw = new PrintWriter(new File(s"results/experiment-get-specific-${bytes.mkString("_")}-$replicate.csv" ))
      results.foreach((element: ExperimentResult) => {
        element.write(pw)
      })
      pw.close()

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
      DU.gGet(currentKey + 1, (k, v) => {
        println(s"Got key $k, value ${new String(v)}")
      })
    }
  }


  def startExperiment(): Unit = {
    val SP = new InMemoryStorageProvider
    val DO = new ChainDataOwner(SP, replicate, ExperimentTools.createGasLogCallback(log, "ChainDataOwnerLogCallback", (_: BigInt) => {
      // Call the get.
      DU.gGet(currentKey, (k, v) => {
        println(s"Got key $k, value ${new String(v)}")
      })
    }), smAddress=smAddress)

    DO.gPuts(createBatch())

    // Keep the code running.
    while(running){}
  }

  def createBatch(): Map[Long, Array[Byte]] = {
    val result = mutable.Map.empty[Long, Array[Byte]]

    // Insert each key in the batch
    for(key <- 0 until bytes.length){
      // Fill the key with a random batch array of size bytes. The byte corresponds to a readable char.
      result(key + 1) = Array.fill(bytes(key))(66.toByte)
    }

    // Return the result.
    result.toMap
  }

  class ExperimentResult(key: Int, bytes: Int, gasCost: BigInt){
    def write(printWriter: PrintWriter): Unit ={
      printWriter.write(s"$key;$bytes;$gasCost\n")
    }
  }
}