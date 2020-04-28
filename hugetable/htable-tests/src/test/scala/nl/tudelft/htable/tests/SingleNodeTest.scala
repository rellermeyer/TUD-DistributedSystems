package nl.tudelft.htable.tests

import java.net.InetSocketAddress

import akka.actor.typed.ActorSystem
import nl.tudelft.htable.client.HTableClient
import nl.tudelft.htable.core.{Node, Row, RowRange, Scan, TabletState}
import nl.tudelft.htable.server.RoundRobinLoadBalancerPolicy
import org.junit.jupiter.api._


/**
 * A test suite for a single node HugeTable cluster.
 */
@DisplayName("Single-node Cluster")
class SingleNodeTest extends AbstractIntegrationTest {
  /**
   * The node that is spawned.
   */
  private val node = Node("test", new InetSocketAddress("localhost", 8818))

  /**
   * The client to access the cluster.
   */
  override protected var client: HTableClient = _

  /**
   * The actor system hosting the server.
   */
  private var server: ActorSystem[Nothing] = _

  /**
   * Setup the HDFS and ZooKeeper cluster.
   */
  @BeforeAll
  override def setUp(): Unit = {
    super.setUp()

    TestUtils.startHDFS()
    TestUtils.hdfs.waitClusterUp()
    TestUtils.startZooKeeper()
    server = TestUtils.startServer(node, new RoundRobinLoadBalancerPolicy())
    // Wait until the server is up
    Thread.sleep(5000)
    client = TestUtils.startClient()
  }

  /**
   * Tear down the HDFS and ZooKeeper cluster.
   */
  @AfterAll
  override def tearDown(): Unit = {
    client.close()
    server.terminate()

    Thread.sleep(1000)
    TestUtils.stopZooKeeper()
    TestUtils.stopHDFS()
  }
}
