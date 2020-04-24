package dynamodb.node

import akka.actor.typed.ActorSystem
import akka.cluster.VectorClock
import dynamodb.node.Node.Stop
import dynamodb.node.ValueRepository.Value
import dynamodb.node.mainObj.NodeConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scalaj.http.{Http, HttpResponse}

import scala.collection.immutable.TreeMap

class ClusterSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  import JsonSupport._
  import spray.json._

  private val node1 = "node1"
  private val node2 = "node2"
  private val node3 = "node3"
  private val node4 = "node4"

  private val host1Config = NodeConfig(BigInt("25"), node1, "localhost", 8001, "localhost", 9001)
  private val host2Config = NodeConfig(BigInt("50"), node2, "localhost", 8002, "localhost", 9002)
  private val host3Config = NodeConfig(BigInt("75"), node3, "localhost", 8003, "localhost", 9003)
  private val host4Config = NodeConfig(BigInt("100"), node4, "localhost", 8004, "localhost", 9004)

  private val host1 = s"http://${host1Config.externalHost}:${host1Config.externalPort}"
  private val host2 = s"http://${host2Config.externalHost}:${host2Config.externalPort}"
  private val host3 = s"http://${host3Config.externalHost}:${host3Config.externalPort}"
  private val host4 = s"http://${host4Config.externalHost}:${host4Config.externalPort}"

  private val hostToUrl = Map(
    node1 -> host1,
    node2 -> host2,
    node3 -> host3,
    node4 -> host4,
  )

  var cluster: List[ActorSystem[Node.Message]] = List()

  override def beforeAll {
    val nodes = List(host1Config, host2Config, host3Config, host4Config)
    val clusterConfig = ClusterConfig(numReplicas = 3, numWriteMinimum = 3, numReadMinimum = 2)

    cluster = nodes.map(n => ActorSystem(Node(n, nodes, clusterConfig), n.name))

    // ActorSytem needs some time to boot, nothing implemented yet to check this.
    Thread.sleep(2400)
  }

  override def afterAll {
    cluster.foreach(n => n ! Stop)
  }

  private def getCoordinatorUrlForKey(key: String): String = {
    val hash = DistributedHashTable.getHash(key)
    if (hash < 25) node1
    else if (hash < 50) node2
    else if (hash < 75) node3
    else node4
  }

  private def get(host: String, path: String) =
    Http(s"$host/values/$path").asString

  private def post[T](host: String, json: T)(implicit writer: JsonWriter[T]): HttpResponse[String] =
    Http(s"$host/values")
      .postData(json.toJson.compactPrint)
      .header("content-type", "application/json")
      .asString

  "The cluster" should {
    /**
     * The cluster is kept online during all the tests, so make sure that you use unique keys for each test.
     */

    "start" in {
      val coordinator = getCoordinatorUrlForKey("myKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, Value("myKey", "myValue"))
        .body should be("Value added")

      // This host should know about it
      get(coordinatorUrl, "myKey")
        .body.parseJson.convertTo[Value] should be(Value("myKey", "myValue", new VectorClock(TreeMap(coordinator -> 0))))

      // It should be replicated here
      get(host1, "myKey")
        .body.parseJson.convertTo[Value] should be(Value("myKey", "myValue", new VectorClock(TreeMap(coordinator -> 0))))
    }

    "update a value" in {
      val coordinator = getCoordinatorUrlForKey("updateKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, Value("updateKey", "myValue"))
        .body shouldBe "Value added"
      post(coordinatorUrl, Value("updateKey", "myOverrideValue", new VectorClock(TreeMap(coordinator -> 0))))
        .body shouldBe "Value added"

      get(coordinatorUrl, "updateKey")
        .body.parseJson.convertTo[Value] should be(Value("updateKey", "myOverrideValue", new VectorClock(TreeMap(coordinator -> 1))))
    }

    "reject overriding a value with wrong version" in {
      val coordinator = getCoordinatorUrlForKey("rejectedKey")
      val coordinatorUrl = hostToUrl(coordinator)

      post(coordinatorUrl, Value("rejectedKey", "myValue"))
        .body shouldBe "Value added"
      post(coordinatorUrl, Value("rejectedKey", "myUpdatedValue", new VectorClock(TreeMap(coordinator -> 0))))
        .body shouldBe "Value added"
      post(coordinatorUrl, Value("rejectedKey", "myOtherUpdatedValue", new VectorClock(TreeMap(coordinator -> 1))))
        .body shouldBe "Value added"
      post(coordinatorUrl, Value("rejectedKey", "myRejectedValue", new VectorClock(TreeMap(coordinator -> 1))))
        .code shouldBe 400
    }
  }
}
