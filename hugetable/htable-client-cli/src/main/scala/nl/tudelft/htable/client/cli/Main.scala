package nl.tudelft.htable.client.cli

import java.io.{ByteArrayInputStream, ObjectInputStream}
import java.net.InetSocketAddress

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.util.ByteString
import nl.tudelft.htable.client.HTableClient
import nl.tudelft.htable.core._
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.utils.ZKPaths
import org.jline.reader.{EndOfFileException, LineReaderBuilder, UserInterruptException}
import org.rogach.scallop.{ScallopConf, ScallopOption, Subcommand, Util}

import scala.collection.Seq
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import scala.jdk.CollectionConverters._

/**
 * Main class of the HugeTable server program.
 */
object Main {

  /**
   * Main entry point of the program.
   *
   * @param args The command line arguments passed to the program.
   */
  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)
    conf.verify()
    val connectionString = conf.zookeeper.getOrElse(List()).mkString(",")
    val zookeeper = CuratorFrameworkFactory.newClient(
      connectionString,
      new ExponentialBackoffRetry(1000, 3)
    )
    zookeeper.start()
    zookeeper.blockUntilConnected()

    val sys = ActorSystem("client")
    implicit val ec: ExecutionContextExecutor = sys.dispatcher
    val client = HTableClient(zookeeper)

    def stop(): Unit = {
      zookeeper.close()
      client.close()
      sys.terminate()
    }

    if (conf.interactive.getOrElse(false)) {
      val reader = LineReaderBuilder
        .builder()
        .appName("htable")
        .build();
      val prompt = "htable $ "
      while (true) {
        var line: Option[String] = None;
        try {
          line = Some(reader.readLine(prompt))
        } catch {
          case _: UserInterruptException => // Ignore
          case _: EndOfFileException =>
            stop()
            return
        }

        line match {
          case Some(value) =>
            if (value.trim.equalsIgnoreCase("exit")) {
              stop()
              return
            }
            try {
              val commands = new Commands(value.split(" "))
              commands.verify()
              val res = Await.result(processCommand(sys, zookeeper, client, commands), 100.seconds)
              println(res)
            } catch {
              case e: Exception => e.printStackTrace()
            }
          case None =>
        }
      }
    } else {
      processCommand(sys, zookeeper, client, conf)
        .onComplete {
          case Failure(exception) =>
            exception.printStackTrace()
            stop()
          case Success(value) =>
            println(value)
            stop()
        }
    }
  }

  /**
   * Process the command in the specified [Conf] object.
   */
  private def processCommand(actorSystem: ActorSystem,
                             zookeeper: CuratorFramework,
                             client: HTableClient,
                             conf: Commands): Future[Done] = {
    implicit val sys: ActorSystem = actorSystem
    implicit val mat: Materializer = Materializer(sys)
    implicit val ec: ExecutionContextExecutor = sys.dispatcher

    conf.subcommand match {
      case Some(conf.get) =>
        client
          .read(Get(conf.get.table(), ByteString(conf.get.key())))
          .runForeach(printRow)
      case Some(conf.scan) =>
        val scan = Scan(conf.scan.table(), RowRange(conf.scan.startKey(), conf.scan.endKey()), conf.scan.reversed())
        client
          .read(scan)
          .runForeach(printRow)
      case Some(conf.put) =>
        val time = conf.put.timestamp.getOrElse(System.currentTimeMillis())
        val mutation: RowMutation = conf.put.cells
          .foldLeft(RowMutation(conf.put.table(), conf.put.key())) {
            case (acc: RowMutation, (qualifier, value)) =>
              acc.put(RowCell(ByteString(qualifier), time, ByteString(value)))
          }
        client
          .mutate(mutation)
      case Some(conf.delete) =>
        val mutation = conf.delete.qualifier.toOption match {
          case Some(value) =>
            RowMutation(conf.delete.table(), conf.delete.key())
              .delete(RowCell(ByteString(value), 0, ByteString.empty))
          case None =>
            RowMutation(conf.delete.table(), conf.delete.key()).delete()
        }
        client
          .mutate(mutation)
      case Some(conf.createTable) =>
        client
          .createTable(conf.createTable.table())
      case Some(conf.deleteTable) =>
        client
          .deleteTable(conf.createTable.table())
      case Some(conf.split) =>
        client
          .split(conf.split.table(), conf.split.splitKey())
      case Some(conf.balance) =>
        client.balance(Set.empty, conf.balance.invalidate())
      case Some(conf.listServers) =>
        zookeeper.getChildren.forPath("/servers").asScala.foreach { node =>
          val address = deserializeAddress(zookeeper.getData.forPath(ZKPaths.makePath("/servers", node)))
          println(s"${node}\t${address}")
        }
        Future.successful(Done)
      case Some(conf.help) =>
        conf.printHelp()
        Future.successful(Done)
      case _ =>
        conf.printHelp()
        Future.successful(Done)
    }
  }

  /**
   * Convert the specified byte string into a socket address.
   */
  def deserializeAddress(bytes: Array[Byte]): InetSocketAddress = {
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject
    ois.close()
    value.asInstanceOf[InetSocketAddress]
  }

  /**
   * Print the contents of a row.
   */
  private def printRow(row: Row): Unit = {
    row.cells.foreach { cell =>
      println(s"${row.key.utf8String}\t${cell.qualifier.utf8String}\t${cell.timestamp}\t${cell.value.utf8String}")
    }
  }

  /**
   * The commands only to parse.
   */
  private class Commands(arguments: Seq[String]) extends ScallopConf(arguments) {

    /**
     * A command to obtain the value of a single row.
     */
    val get = new Subcommand("get") {

      /**
       * The table to read from.
       */
      val table = trailArg[String](required = true)

      /**
       * The key to read.
       */
      val key = trailArg[String](required = true)
    }
    addSubcommand(get)

    /**
     * A command to perform a scan of a table.
     */
    val scan = new Subcommand("scan") {

      /**
       * The table to read from.
       */
      val table = trailArg[String](required = true)

      /**
       * The start key to scan from.
       */
      val startKey = opt[String](default = Some(""), required = false).map(s => ByteString(s))

      /**
       * The end key to scan from.
       */
      val endKey = opt[String](default = Some(""), required = false).map(s => ByteString(s))

      /**
       * A flag to make the scan reversed.
       */
      val reversed = opt[Boolean](default = Some(false))
    }
    addSubcommand(scan)

    /**
     * Add a row to a table.
     */
    val put = new Subcommand("put") {

      /**
       * The table to add the row to.
       */
      val table = trailArg[String](required = true)

      /**
       * The key to delete
       */
      val key = trailArg[String]().map(s => ByteString(s))

      /**
       * The cell to append.
       */
      val cells = propsLong[String]("cells")

      /**
       * The timestamp of the row.
       */
      val timestamp = opt[Long](short = 't')
    }
    addSubcommand(put)

    /**
     * Delete a row from a table.
     */
    val delete = new Subcommand("delete") {

      /**
       * The table to delete the row/cell from.
       */
      val table = trailArg[String](required = true)

      /**
       * The key to delete
       */
      val key = trailArg[String](default = Some("")).map(s => ByteString(s))

      /**
       * The cell to delete
       */
      val qualifier = opt[String](required = false).map(s => ByteString(s))
    }
    addSubcommand(delete)

    /**
     * A command to perform a table creation.
     */
    val createTable = new Subcommand("create-table") {

      /**
       * The table to create
       */
      val table = trailArg[String](required = true)
    }
    addSubcommand(createTable)

    /**
     * A command to perform a table deletion.
     */
    val deleteTable = new Subcommand("delete-table") {

      /**
       * The table to create
       */
      val table = trailArg[String](required = true)
    }
    addSubcommand(deleteTable)

    /**
     * A command to perform a tablet split.
     */
    val split = new Subcommand("split") {

      /**
       * The table to split.
       */
      val table = trailArg[String](required = true)

      /**
       * The split point.
       */
      val splitKey = trailArg[String](descr = "The key at which to split the table", required = true).map(ByteString(_))
    }
    addSubcommand(split)

    /**
     * A command to balance the current assignments.
     */
    val balance = new Subcommand("balance") {

      /**
       * A flag to invalidate the entire assignment.
       */
      val invalidate = opt[Boolean](descr = "Invalidate the entire assigment", default = Some(false))
    }
    addSubcommand(balance)

    /**
     * A command to list the active servers.
     */
    val listServers = new Subcommand("list-servers")
    addSubcommand(listServers)

    /**
     * A command to print a help message.
     */
    val help = new Subcommand("help")
    addSubcommand(help)

    errorMessageHandler = { message =>
      println(Util.format("Error: %s", message))
    }
  }

  /**
   * The command line configuration of the application.
   *
   * @param arguments The command line arguments passed to the program.
   */
  private class Conf(arguments: Seq[String]) extends Commands(arguments) {

    /**
     * An option for specifying the ZooKeeper addresses to connect to.
     */
    val zookeeper: ScallopOption[List[String]] = opt[List[String]](
      short = 'z',
      descr = "The ZooKeeper addresses to connect to",
      required = true
    )

    /**
     * A flag to make an interactive console.
     */
    val interactive = opt[Boolean](short = 'i', descr = "Interactive console")

    errorMessageHandler = { message =>
      Console.err.println(Util.format("[%s] Error: %s", printedName, message))
      sys.exit(1)
    }
  }
}
