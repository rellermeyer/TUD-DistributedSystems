package nl.tudelft.htable.server.cli

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory
import nl.tudelft.htable.core.Node
import nl.tudelft.htable.server.{
  MinTabletsLoadBalancerPolicy,
  HTableActor,
  RandomLoadBalancerPolicy,
  RoundRobinLoadBalancerPolicy
}
import nl.tudelft.htable.storage.hbase.HBaseStorageDriverProvider
import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.ExponentialBackoffRetry
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.rogach.scallop.{ScallopConf, ScallopOption}

import scala.collection.Seq

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
    val connectionString = conf.zookeeper.getOrElse(List()).mkString(",")
    val zookeeper = CuratorFrameworkFactory.newClient(connectionString, new ExponentialBackoffRetry(1000, 3))

    // Important: enable HTTP/2 in ActorSystem's config
    // We do it here programmatically, but you can also set it in the application.conf
    val actorConf = ConfigFactory
      .parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())

    val hconf = new Configuration()
    hconf.set("fs.defaultFS", conf.hadoop())
    val fs = FileSystem.get(hconf)

    val loadBalancerPolicy = conf.loadBalancerPolicy() match {
      case "round-robin" => new RoundRobinLoadBalancerPolicy()
      case "random"      => new RandomLoadBalancerPolicy()
      case "min-tablets" => new MinTabletsLoadBalancerPolicy()
      case value =>
        throw new IllegalArgumentException(s"Unknown load balancer policy $value")
    }

    val node = Node(UUID.randomUUID().toString, new InetSocketAddress(conf.address(), conf.port()))
    val driver = new HBaseStorageDriverProvider(fs)
    ActorSystem(HTableActor(node, zookeeper, driver, loadBalancerPolicy), "htable", actorConf)
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
      descr = "The ZooKeeper addresses to connect to",
      required = true
    )

    /**
     * An option for specifying the HDFS addresses to connect to.
     */
    val hadoop: ScallopOption[String] = opt[String](
      descr = "The Hadoop address to connect to",
      required = true
    )

    /**
     * An option for specifying the port to connect to.
     */
    val port: ScallopOption[Int] = opt[Int](short = 'p', descr = "The port to connect to", required = true)

    /**
     * An option for specifying the address to connect to.
     */
    val address: ScallopOption[String] =
      opt[String](short = 'a', descr = "The address to connect to", default = Some("localhost"))

    /**
     * An option for specifying the load balancing policy.
     */
    val loadBalancerPolicy: ScallopOption[String] = opt[String](
      descr = "The load balancing policy to use",
      default = Some("round-robin")
    )

    verify()
  }
}
