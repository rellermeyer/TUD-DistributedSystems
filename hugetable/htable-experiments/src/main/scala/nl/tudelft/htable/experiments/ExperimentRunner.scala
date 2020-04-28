package nl.tudelft.htable.experiments

import java.io.{File, PrintWriter}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.LongAdder

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import nl.tudelft.htable.client.HTableClient
import nl.tudelft.htable.core._
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand}

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, mutable}
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.Random
import scala.jdk.CollectionConverters._


/**
 * A tool for running experiments on top of HugeTable.
 */
object ExperimentRunner {

  /**
   * The logging instance to use.
   */
  private val logger = Logger[ExperimentRunner.type]

  /**
   * Akka setup
   */
  implicit val sys: ActorSystem = ActorSystem("client")
  implicit val mat: Materializer = Materializer(sys)
  implicit val ec: ExecutionContextExecutor = sys.dispatcher

  /**
   * Main entry point of the program.
   *
   * @param args The command line arguments passed to the program.
   */
  def main(args: Array[String]): Unit = {
    logger.info("Starting HugeTable experiment runner")
    logger.info("Parsing command line arguments")
    val conf = new Conf(args)

    val connectionString = conf.zookeeper.getOrElse(List()).mkString(",")
    logger.info(s"Connecting to ZooKeeper cluster: $connectionString")
    val zookeeper = CuratorFrameworkFactory.newClient(
      connectionString,
      new ExponentialBackoffRetry(1000, 3)
    )
    zookeeper.start()
    zookeeper.blockUntilConnected()

    conf.subcommand match {
      case Some(conf.read) =>
        runExperiment(conf, zookeeper, "read")
      case Some(conf.write) =>
        runExperiment(conf, zookeeper, "write")
      case _ =>
        logger.error("Unknown subcommand")
    }
    zookeeper.close()
    sys.terminate()
  }

  /**
   * Run the initial setup to create the database structure.
   */
  private def runSetup(conf: Conf, zookeeper: CuratorFramework, table: String): Unit = {
    val client = HTableClient(zookeeper)

    logger.info(s"Creating table: $table")
    Await.ready(client.createTable(table), 100.seconds)

    val maxSize = conf.maxRows()
    val splits = conf.splits()
    val splitSize = maxSize / splits

    logger.info(s"Creating ${splits} split points")

    for (i <- 1 until splits) {
      val splitPoint = s"row_${maxSize - (i * splitSize)}"
      logger.info(s"Split at $splitPoint")
      Await.ready(client.split(table, splitKey = ByteString(splitPoint)), 100.seconds)

      // Wait a bit for the change to process
      Thread.sleep(1000)
    }

    logger.info("Done")
    client.close()
  }

  /**
   * Tear down a table.
   */
  private def runTearDown(conf: Conf, zookeeper: CuratorFramework, table: String): Unit = {
    val client = HTableClient(zookeeper)

    logger.info(s"Delete table: $table")
    Await.ready(client.deleteTable(table), 100.seconds)

    logger.info("Done")
    client.close()
  }

  /**
   * Run an experiment.
   */
  private def runExperiment(conf: Conf, zookeeper: CuratorFramework, name: String): Unit = {
    logger.info(s"Starting $name experiment")
    val pw = new PrintWriter(new File(s"experiment-$name-${System.currentTimeMillis()}.json"))

    for (i <- 1 to conf.repeats()) {
      val table = if (name == "read") "1" else i.toString
      if (name == "write")
        runSetup(conf, zookeeper, table)
      runTrial(conf, zookeeper, table, i, conf.repeats(), name == "read", pw)
      if (name == "write")
        runTearDown(conf, zookeeper, table)
    }

    pw.close()
  }

  /**
   * Run an trial of the experiment.
   */
  private def runTrial(conf: Conf, zookeeper: CuratorFramework, table: String, trial: Int, total: Int, read: Boolean,
                       writer: PrintWriter): Unit = {
    logger.info(s"Starting trial $trial/$total")

    val trialStart = System.currentTimeMillis()
    val totalRequests = new LongAdder()
    val maxRows = conf.maxRows()
    val totalTimings = new ConcurrentLinkedQueue[ArrayBuffer[Long]]()

    val col = ByteString("column")
    val value = ByteString("a" * 1000) // We write 1000 byte values per request (same as big table paper)

    val futures = (0 until conf.threads()).map { t =>
      Future {
        val timings = mutable.ArrayBuffer[Long]()
        val client = HTableClient(zookeeper)
        val random = new Random()
        val opsPerThread = conf.ops() / conf.threads()

        for (i <- 0 until opsPerThread) {
          val nextRow = random.nextInt(maxRows)
          val start = System.currentTimeMillis()
          val result =
            if (read) {
              val scan: Scan = Scan(table, RowRange.prefix(ByteString("row" + nextRow)))
              client.read(scan).runFold(0)( (acc, _) => acc + 1)
            } else {
              val mutation: RowMutation = RowMutation(table, ByteString("row_" + nextRow))
                .put(RowCell(col, start, value))
              client.mutate(mutation)
            }

          try {
            Await.result(result, 100.seconds)
          } catch {
            case e: Throwable =>
              logger.error("Failed to run operation", e)
              client.close()
              throw e
          }

          val end = System.currentTimeMillis()
          val duration = end - start
          totalRequests.increment()
          timings.addOne(duration)

          if (i % 1000 == 0) {
            val throughput = (totalRequests.longValue() / (end - trialStart).toDouble) * 1000
            logger.info(s"$trial/$total [$t] At $i/$opsPerThread ($throughput ops/s)")
          }
        }

        totalTimings.add(timings)
        client.close()
      }
    }

    Await.result(Future.sequence(futures), 10.minutes)

    val trialEnd = System.currentTimeMillis()
    val trialDuration = trialEnd - trialStart
    val throughput = (totalRequests.longValue() / trialDuration.toDouble) * 1000
    val timingsString = totalTimings.asScala.flatten.mkString(",")
    writer.println(
      s"""
         |{
         | "trial": ${trial},
         | "duration": ${trialDuration},
         | ${conf.extra.map(p => s""" "${p._1}" : "${p._2}", """).mkString("\n")}
         | "throughput": ${throughput},
         | "timings": [$timingsString]
         |}${if (trial == total) "" else ","}
         |""".stripMargin
    )
    writer.flush()
    logger.info(s"Finished trial $trial/$total: ${trialDuration / 1000} seconds ($throughput ops/s)")
  }

  /**
   * The command line configuration of the application.
   *
   * @param arguments The command line arguments passed to the program.
   */
  private class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {

    /**
     * An option for specifying the ZooKeeper addresses to connect to.
     */
    val zookeeper: ScallopOption[List[String]] = opt[List[String]](
      short = 'z',
      descr = "The ZooKeeper addresses to connect to",
      required = true
    )

    /**
     * An option for specifying the number of operations to perform in total.
     */
    val ops: ScallopOption[Int] = opt[Int](
      short = 'o',
      descr = "The number of operations to perform in total",
      default = Some(100_000)
    )

    /**
     * An option for specifying the maximum number of rows.
     */
    val maxRows: ScallopOption[Int] = opt[Int](
      descr = "The maximum number of rows to include in the experiments",
      default = Some(10_000)
    )

    /**
     * The number of repeats to perform.
     */
    val repeats: ScallopOption[Int] = opt[Int](
      descr = "The number of repeats to perform",
      default = Some(8)
    )

    /**
     * An option for specifying the number of threads to read from.
     */
    val threads: ScallopOption[Int] = opt[Int](
      short = 'r',
      descr = "The number of threads to use",
      default = Some(8)
    )

    /**
     * The number of splits to perform.
     */
    val splits: ScallopOption[Int] = opt[Int](
      descr = "The number of splits to perform",
      default = Some(8)
    )

    /**
     * An option for appending extra information to the resulting file.
     */
    val extra: Map[String, String] = propsLong[String]("extra")

    /**
     * Perform a READ experiment
     */
    val read = new Subcommand("read")
    addSubcommand(read)

    /**
     * Perform a WRITE experiment
     */
    val write = new Subcommand("write")
    addSubcommand(write)

    verify()
  }

}
