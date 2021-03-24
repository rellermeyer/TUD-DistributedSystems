import FileSystem.DistributedSystem
import VotingSystem.FileSuiteManager

import java.io.{BufferedWriter, FileWriter}
import scala.collection.mutable.ListBuffer
import scala.util.Random
import com.opencsv.CSVWriter

import scala.jdk.CollectionConverters._

object Experiment {

  val readProportion: Double = 0.5
  val numCalls: Int = 10000

  val numContainers: Int = 10
  val latencies: Seq[Int] = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
  val failureProbs: Seq[Double] = Seq(0.0, 0.1, 0.2, 0.3, 0.4, 0.5)

  val suiteId: Int = 1
  val suiteR: Int = 6
  val suiteW: Int = 6
  val repWeights: Seq[Int] = Seq(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)

  def main(args: Array[String]): Unit = {

    val r: Random = scala.util.Random
    var event: Double = r.nextDouble()

    val file = new BufferedWriter(new FileWriter("C:\\Users\\blok_\\Desktop\\Results.csv"))
    val csvWriter = new CSVWriter(file)
    val records = new ListBuffer[Array[String]]()
    val csvColumns = Array("container failure probability", "successful calls", "failed calls", "average latency")
    records += csvColumns

    for (p <- failureProbs) {
      val fileSystem = DistributedSystem(numContainers, latencies, p)
      val manager = FileSuiteManager(fileSystem)
      fileSystem.createRepresentatives(suiteId, suiteR, suiteW, repWeights)
      var successCount: Int = 0
      var failCount: Int = 0
      var totalLatency: Int = 0
      var inconsintencyCount: Int = 0
      var fileContent: Int = -1

      for (i <- 0 until numCalls) {
        if (event >= readProportion) {
          val result = manager.read(suiteId)
          result match {
            case Left(f) => failCount = failCount + 1
            case Right(r) => {
              successCount = successCount + 1
              totalLatency = totalLatency + r._2
            }
          }
        }
        else {
          val result = manager.write(suiteId, 1)
          result match {
            case Left(f) => failCount = failCount + 1
            case Right(r) => {
              successCount = successCount + 1
              totalLatency = totalLatency + r
            }
          }
        }
        event = r.nextDouble()
      }
      val avgLatency: Double = totalLatency.toDouble/successCount.toDouble
      println("p: " + p + ", successes: " + successCount + ", failures: " + failCount + ", avg latency: " + avgLatency)
      records += Array(p.toString, successCount.toString , failCount.toString , avgLatency.toString)
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
