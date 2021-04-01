import FileSystem.FileSystem
import VotingSystem.FileSuiteManager

import java.io.{BufferedWriter, FileWriter}
import scala.collection.mutable.ListBuffer
import scala.util.Random
import com.opencsv.CSVWriter

import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}

object Experiment {

  val outputPath: String = "C:\\Users\\blok_\\Desktop\\Results.csv"

  val numContainers: Int = 5

  val latencies: Seq[Seq[Int]] = Seq(Seq(1, 2, 4, 8, 16))
  val blockingProbs: Seq[Seq[Double]] = Seq(Seq(0.00, 0.10, 0.00, 0.10, 0.10))//, 0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50)

  val readPortions: Seq[Double] = Seq(0.5)
  val transactionLengths: Seq[Int] = Seq(1)
  val numTransactions: Int = 10000

  val suiteId: Int = 1
  val suiteRWPairs: Seq[(Int, Int)] = Seq((9, 11))
  val repWeights: Seq[Seq[Int]] = Seq(Seq(4, 4, 4, 4, 4))

  def main(args: Array[String]): Unit = {

    val r: Random = scala.util.Random
    var event: Double = r.nextDouble()

    val file = new BufferedWriter(new FileWriter(outputPath))
    val csvWriter = new CSVWriter(file)
    val records = new ListBuffer[Array[String]]()
    val csvColumns = Array(
      "latencies",
      "blocking probability",
      "read percentage",
      "write percentage",
      "transaction length",
      "transaction count",
      "r",
      "w",
      "representative weights",
      "successful call percentage",
      "failed call percentage",
      "transaction commit percentage",
      "transaction abort percentage",
      "average call latency",
      "consistency percentage"
    )
    records += csvColumns

    for (latency <- latencies;
         blockingProb <- blockingProbs;
         readPortion <- readPortions;
         transactionLength <- transactionLengths;
         rw <- suiteRWPairs;
         weights <- repWeights) {

      val fileSystem = FileSystem(numContainers, latency, blockingProb)
      val manager = FileSuiteManager(fileSystem)
      fileSystem.createRepresentatives(suiteId, rw._1, rw._2, weights)
      var successCount: Int = 0
      var commitCount: Int = 0
      var totalLatency: Int = 0
      var consintencyCount: Int = 0
      var fileContent: Int = -1

      for (i <- 0 until numTransactions) {
        var aborted: Boolean = false

        manager.begin()
        breakable { for (j <- 0 until transactionLength) {
          if (event <= readPortion) {
            val result = manager.read(suiteId)
            result match {
              case Left(f) => {}
              case Right(r) => {
                successCount += 1
                totalLatency += r._2
              }
            }
          }
          else {
            val result = manager.write(suiteId, 1)
            result match {
              case Left(f) => {
                manager.abort()
                aborted = true
                break
              }
              case Right(r) => {
                successCount += 1
                totalLatency += r
              }
            }
          }
          event = r.nextDouble()
        }}

        if (!aborted) {
          commitCount += 1
          manager.commit()
        }
      }

      val avgLatency: Double = totalLatency.toDouble / successCount.toDouble
      val successCallPercentage: Double = (successCount.toDouble / (numTransactions.toDouble * transactionLength.toDouble)) * 100.0
      val failCallPercentage: Double = 100.0 - successCallPercentage
      val commitPercentage: Double = (commitCount.toDouble / numTransactions.toDouble) * 100.0
      val abortPercentage: Double = 100.0 - commitPercentage
//      val transactionSuccessPercentage = (transactionSuccess.toDouble / numTransactions.toDouble) * 100
//      records += Array(blockingProb.toString, readPortion.toString, rw._1.toString, rw._2.toString,
//        successPercentage.toString , failPercentage.toString , avgLatency.toString, transactionSuccessPercentage.toString)

      println(
        "Experiment statistics: " + "\n" +
        "latencies: " + latency + "\n" +
        "blocking probability: " + blockingProb + "\n" +
        "read percentage: " + readPortion * 100.0 + "\n" +
        "write percentage: " + (1.0 - readPortion) * 100 + "\n" +
        "transaction length: " + transactionLength + "\n" +
        "transaction count: " + numTransactions + "\n" +
        "r: " + rw._1 + "\n" +
        "w: " + rw._2 + "\n" +
        "representative weights: " + weights + "\n" +
        "successful call percentage: " + successCallPercentage + "\n" +
        "failed call percentage: " + failCallPercentage + "\n" +
        "transaction commit percentage: " + commitPercentage + "\n" +
        "transaction abort percentage: " + abortPercentage + "\n" +
        "average call latency: " + avgLatency + "\n" +
        "consistency percentage: TODO" + "\n\n"
      )
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
