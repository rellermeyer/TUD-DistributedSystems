package nl.tudelft.htable.test.env

import java.io.File

import com.typesafe.scalalogging.Logger
import org.apache.curator.test.TestingServer
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hdfs.{DFSConfigKeys, MiniDFSCluster, MiniDFSNNTopology}
import org.rogach.scallop.{ScallopConf, ScallopOption}

import scala.collection.Seq

/**
 * Main class of the HugeTable server program.
 */
object Main {

  /**
   * The logging instance of this class.
   */
  private val log = Logger[Main.type]

  /**
   * Main entry point of the program.
   *
   * @param args The command line arguments passed to the program.
   */
  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)

    log.info("Starting embedded test environment")

    if (!conf.disableZookeeper()) {
      log.info(s"Starting ZooKeeper test cluster on port ${conf.zookeeperPort()}")
      val zookeeper = startZooKeeper(conf)
      zookeeper.start()
    }

    if (!conf.disableHdfs()) {
      log.info(s"Starting Hadoop Mini HDFS cluster on port ${conf.hdfsPort()}")
      val hdfs = startHDFS(conf)
      hdfs.waitClusterUp()
    }
  }

  /**
   * Create a mini Hadoop HDFS cluster.
   */
  def startHDFS(conf: Conf): MiniDFSCluster = {
    val hdfsConf = new Configuration()
    hdfsConf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, conf.hdfsData().getAbsolutePath)
    hdfsConf.setBoolean("dfs.webhdfs.enabled", true)
    val builder = new MiniDFSCluster.Builder(hdfsConf)
    builder
      .nameNodePort(conf.hdfsPort())
      .manageNameDfsDirs(true)
      .manageDataDfsDirs(true)
      .format(true)
      .build()
  }

  /**
   * Create a ZooKeeper test cluster.
   */
  def startZooKeeper(conf: Conf): TestingServer = new TestingServer(conf.zookeeperPort())

  /**
   * The command line configuration of the application.
   *
   * @param arguments The command line arguments passed to the program.
   */
  private class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {

    /**
     * A flag to disable ZooKeeper.
     */
    val disableZookeeper: ScallopOption[Boolean] = opt[Boolean](descr = "Disable ZooKeeper", default = Some(false))

    /**
     * An option for specifying the ZooKeeper port to listen to.
     */
    val zookeeperPort: ScallopOption[Int] = opt[Int](descr = "The ZooKeeper port to listen to", default = Some(2181))

    /**
     * A flag to disable HDFS.
     */
    val disableHdfs: ScallopOption[Boolean] = opt[Boolean](descr = "Disable HDFS", default = Some(false))

    /**
     * An option for specifying the HDFS port to listen to.
     */
    val hdfsPort: ScallopOption[Int] = opt[Int](descr = "The HDFS port to listen to", default = Some(9000))

    /**
     * An option for specifying the HDFS data path to store the data.
     */
    val hdfsData: ScallopOption[File] =
      opt[File](descr = "The path to store the HDFS data", default = Some(new File("hdfs-data")))
    verify()
  }
}
