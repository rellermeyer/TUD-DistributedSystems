import FileSystem.DistributedSystem
import VotingSystem.FileSuiteManager

import java.io.{BufferedWriter, FileWriter}
import scala.collection.mutable.ListBuffer
import scala.util.Random
import com.opencsv.CSVWriter

import scala.jdk.CollectionConverters._

object Experiment {

  val readProportions: Seq[Double] = Seq(1.0)
  val numCalls: Int = 10000

  val numContainers: Int = 10
  val latencies: Seq[Int] = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val failureProbs: Seq[Double] = Seq(0.00, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50)

  val suiteId: Int = 1
  val suiteRWPairs: Seq[(Int, Int)] = Seq((6, 6), (7, 7))
  val repWeights: Seq[Int] = Seq(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)

  def main(args: Array[String]): Unit = {

    val r: Random = scala.util.Random
    var event: Double = r.nextDouble()

    val file = new BufferedWriter(new FileWriter("C:\\Users\\blok_\\Desktop\\Results.csv"))
    val csvWriter = new CSVWriter(file)
    val records = new ListBuffer[Array[String]]()
    val csvColumns = Array("container failure probability", "read proportion", "r", "w", "successful call percentage",
      "failed call percentage", "average latency")
    records += csvColumns

    for (fProb <- failureProbs; readProp <- readProportions; rw <- suiteRWPairs) {
      val fileSystem = DistributedSystem(numContainers, latencies, fProb)
      val manager = FileSuiteManager(fileSystem)
      fileSystem.createRepresentatives(suiteId, rw._1, rw._2, repWeights)
      var successCount: Int = 0
      var failCount: Int = 0
      var totalLatency: Int = 0
      var inconsintencyCount: Int = 0
      var fileContent: Int = -1

      for (i <- 0 until numCalls) {
        if (event <= readProp) {
          val result = manager.read(suiteId)
          result match {
            case Left(f) => {
              failCount = failCount + 1
              //print(f.reason)
            }
            case Right(r) => {
              successCount = successCount + 1
              totalLatency = totalLatency + r._2
            }
          }
        }
        else {
          val result = manager.write(suiteId, 1)
          result match {
            case Left(f) => {
              failCount = failCount + 1
              //print(f.reason)
            }
            case Right(r) => {
              successCount = successCount + 1
              totalLatency = totalLatency + r
            }
          }
        }
        event = r.nextDouble()
      }
      val avgLatency: Double = totalLatency.toDouble/successCount.toDouble
      println("failure probability: " + fProb + ", read proportion: " + readProp + ", r: " + rw._1 + ", w: " + rw._2 +
        ", successes: " + (successCount.toDouble/numCalls.toDouble)*100.0 + ", failures: "
        + (failCount.toDouble/numCalls.toDouble)*100.0 + ", avg latency: " + avgLatency)

      val successPercentage = (successCount.toDouble / numCalls.toDouble) * 100.0
      val failPercentage = (failCount.toDouble / numCalls.toDouble) * 100.0
      records += Array(fProb.toString, readProp.toString, rw._1.toString, rw._2.toString,
        successPercentage.toString , failPercentage.toString , avgLatency.toString)
    }
    try {
      csvWriter.writeAll(records.toList.asJava)
    }
    catch {
      case e: java.io.IOException => println("failed to write")
    }
    file.close()
  }
}
