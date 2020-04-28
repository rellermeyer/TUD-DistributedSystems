package nl.tudelft.htable.tests

import java.net.InetSocketAddress

import akka.actor.typed.ActorSystem
import nl.tudelft.htable.client.HTableClient
import nl.tudelft.htable.core.Node
import nl.tudelft.htable.server.RandomLoadBalancerPolicy
import org.junit.jupiter.api.{AfterAll, BeforeAll, DisplayName}

/**
 * A test suite for a multiple-node HugeTable cluster.
 */
@DisplayName("Multi-node Cluster")
class ClusterTest extends AbstractIntegrationTest {
  /**
   * The number of nodes to spawn.
   */
  private val nodes = 4

  /**
   * The client to access the cluster.
   */
  override protected var client: HTableClient = _

  /**
   * The actor system hosting the server.
   */
  private var servers: Seq[ActorSystem[Nothing]] = Seq.empty

  /**
   * Setup the HDFS and ZooKeeper cluster.
   */
  @BeforeAll
  override def setUp(): Unit = {
    super.setUp()

    TestUtils.startHDFS()
    TestUtils.hdfs.waitClusterUp()
    TestUtils.startZooKeeper()


    servers = (0 until nodes).map { index =>
      val node = Node("test-" + index, new InetSocketAddress("localhost", 8818 + index))
      TestUtils.startServer(node, new RandomLoadBalancerPolicy())
    }
    // Wait until the servers are up
    Thread.sleep(5000)

    client = TestUtils.startClient()
  }

  /**
   * Tear down the HDFS and ZooKeeper cluster.
   */
  @AfterAll
  override def tearDown(): Unit = {
    super.tearDown()

    client.close()
    servers.foreach(_.terminate())

    Thread.sleep(1000)
    TestUtils.stopZooKeeper()
    TestUtils.stopHDFS()
  }
}
