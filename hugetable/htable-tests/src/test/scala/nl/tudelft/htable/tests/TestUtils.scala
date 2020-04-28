package nl.tudelft.htable.tests

import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.UUID

import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory
import nl.tudelft.htable.client.{HTableClient, HTableInternalClient}
import nl.tudelft.htable.core.Node
import nl.tudelft.htable.server.{HTableActor, LoadBalancerPolicy}
import nl.tudelft.htable.storage.hbase.HBaseStorageDriverProvider
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.curator.test.TestingServer
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hdfs.MiniDFSCluster

/**
 * Utilities for managing tests.
 */
object TestUtils {
  /**
   * The current active HDFS cluster.
   */
  private var dfsCluster: MiniDFSCluster = _

  /**
   * Create a mini Hadoop HDFS cluster.
   */
  def startHDFS(): MiniDFSCluster = {
    val hdfsConf = new Configuration()
    hdfsConf.set(MiniDFSCluster.HDFS_MINIDFS_BASEDIR, Files.createTempDirectory("htable").toString)
    hdfsConf.setBoolean("dfs.webhdfs.enabled", true)
    hdfsConf.set("hadoop.root.logger", "WARN")

    val builder = new MiniDFSCluster.Builder(hdfsConf)
    dfsCluster = builder
      .manageNameDfsDirs(true)
      .manageDataDfsDirs(true)
      .format(true)
      .build()
    dfsCluster
  }

  /**
   * Obtain the HDFS cluster.
   */
  def hdfs: MiniDFSCluster = dfsCluster

  /**
   * Stop the mini Hadoop cluster.
   */
  def stopHDFS(): Unit = {
    dfsCluster.close()
    dfsCluster = null
  }

  /**
   * The current active ZooKeeper test cluster.
   */
  private var zkTestingServer: TestingServer = _

  /**
   * Create a ZooKeeper test cluster.
   */
  def startZooKeeper(): TestingServer = {
    zkTestingServer = new TestingServer()
    zkTestingServer.start()
    zkTestingServer
  }

  /**
   * Obtain the ZooKeeper testing server.
   */
  def zookeeper: TestingServer = zkTestingServer

  /**
   * Stop a ZooKeeper cluster.
   */
  def stopZooKeeper(): Unit = {
    zkTestingServer.close()
    zkTestingServer = null
  }

  /**
   * Start an appropriate actor system for server and client.
   */
  def startActorSystem[T](behavior: Behavior[T]): ActorSystem[T] = {
    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val actorConf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())

    ActorSystem(behavior, UUID.randomUUID().toString, actorConf)
  }

  /**
   * Start a new server node.
   */
  def startServer(node: Node, loadBalancerPolicy: LoadBalancerPolicy): ActorSystem[Nothing] = {
    val zookeeper = CuratorFrameworkFactory.newClient(this.zookeeper.getConnectString, new ExponentialBackoffRetry(1000, 3))
    val driver = new HBaseStorageDriverProvider(hdfs.getFileSystem())
    startActorSystem(HTableActor(node, zookeeper, driver, loadBalancerPolicy))
  }

  /**
   * Start a new client.
   */
  def startClient(): HTableClient = {
    val zookeeper = CuratorFrameworkFactory.newClient(this.zookeeper.getConnectString, new ExponentialBackoffRetry(1000, 3))
    zookeeper.start()
    zookeeper.blockUntilConnected()
    HTableClient(zookeeper)
  }
}
