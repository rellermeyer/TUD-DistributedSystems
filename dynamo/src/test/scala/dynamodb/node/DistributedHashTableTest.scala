package dynamodb.node

import java.security.MessageDigest

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import dynamodb.node.DistributedHashTable.{AddNode, GetRing, GetTopN, OK, Response}
import dynamodb.node.ring.{Ring, RingNode}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await.result
import scala.concurrent.duration._

class DistributedHashTableTest extends AnyFlatSpec with Matchers with BeforeAndAfter {
  val testKit: ActorTestKit = ActorTestKit()

  implicit val system: ActorSystem[Nothing] = testKit.system
  implicit val timeout: Timeout = testKit.timeout

  import akka.actor.typed.scaladsl.AskPattern._

  "abc" should "result in correct hash " in {
    val res = DistributedHashTable.getHash("abc")
    val hash = MessageDigest.getInstance("MD5").digest("abc".getBytes)
    val hexString = DistributedHashTable.convertBytesToHex(hash)
    res should be(BigInt(70))
  }

  "add node" should "add a node to the ring" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000, "localhost", 9000, "node")

    val ring = DistributedHashTable.createRing(List(node1))

    val dht = testKit.spawn(DistributedHashTable(ring, 1))

    val node2 = RingNode(BigInt(10), "localhost", 8001, "localhost", 9001, "node")

    result(dht.ask(AddNode(node2, _: ActorRef[Response])), 1.second) should be(OK)

    val dhtRing = result(dht.ask(GetRing(_: ActorRef[Ring])), 1.second)

    dhtRing.head should be(node1)
    dhtRing(1) should be(node2)
  }

  "nodes" should "be ordered" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000, "localhost", 9000, "node")
    val node2 = RingNode(BigInt(2), "localhost", 8000, "localhost", 9000, "node")
    val node3 = RingNode(BigInt(3), "localhost", 8000, "localhost", 9000, "node")
    val node4 = RingNode(BigInt(4), "localhost", 8000, "localhost", 9000, "node")

    val dht = testKit.spawn(DistributedHashTable())

    result(dht.ask(AddNode(node1, _: ActorRef[Response])), 1.second)
    result(dht.ask(AddNode(node4, _: ActorRef[Response])), 1.second)
    result(dht.ask(AddNode(node3, _: ActorRef[Response])), 1.second)
    result(dht.ask(AddNode(node2, _: ActorRef[Response])), 1.second)

    val list = result(dht.ask(GetRing(_: ActorRef[Ring])), 1.second)

    list should be(List(node1, node2, node3, node4))
  }

  "preference list" should "return top N nodes" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000, "localhost", 9000, "node")
    val node2 = RingNode(BigInt(100), "localhost", 8001, "localhost", 9001, "node")
    val node3 = RingNode(BigInt(200), "localhost", 8002, "localhost", 9002, "node")
    val node4 = RingNode(BigInt(300), "localhost", 8003, "localhost", 9003, "node")
    val node5 = RingNode(BigInt(400), "localhost", 8004, "localhost", 9004, "node")

    val ring = DistributedHashTable.createRing(List(node1, node2, node3, node4, node5))

    val dht = testKit.spawn(DistributedHashTable(ring, 5))

    val list = result(dht.ask(GetTopN(150, 3, _: ActorRef[Option[Ring]])), 1.second)
    list should be(Some(List(node3, node4, node5)))
  }

  "preference list" should "return top N nodes circularly for small values" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000, "localhost", 9000, "node")
    val node2 = RingNode(BigInt(10), "localhost", 8001, "localhost", 9001, "node")
    val node3 = RingNode(BigInt(20), "localhost", 8002, "localhost", 9002, "node")
    val node4 = RingNode(BigInt(60), "localhost", 8003, "localhost", 9003, "node")
    val node5 = RingNode(BigInt(80), "localhost", 8004, "localhost", 9004, "node")

    val ring = DistributedHashTable.createRing(List(node1, node2, node3, node4, node5))

    val dht = testKit.spawn(DistributedHashTable(ring, 5))

    val list = result(dht.ask(GetTopN(50, 4, _: ActorRef[Option[Ring]])), 1.second)
    list should be(Some(List(node4, node5, node1, node2)))
  }

  "preference list" should "return top N nodes circularly" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000, "localhost", 9000, "node")
    val node2 = RingNode(BigInt(100), "localhost", 8001, "localhost", 9001, "node")
    val node3 = RingNode(BigInt(200), "localhost", 8002, "localhost", 9002, "node")
    val node4 = RingNode(BigInt(300), "localhost", 8003, "localhost", 9003, "node")
    val node5 = RingNode(BigInt(400), "localhost", 8004, "localhost", 9004, "node")

    val ring = DistributedHashTable.createRing(List(node1, node2, node3, node4, node5))

    val dht = testKit.spawn(DistributedHashTable(ring, 5))

    val list = result(dht.ask(GetTopN(250, 4, _: ActorRef[Option[Ring]])), 1.second)
    list should be(Some(List(node4, node5, node1, node2)))
  }

  "preference list" should "throw an error if hash is too large" in {
    val node1 = RingNode(BigInt(1), "localhost", 8000, "localhost", 9000, "node")
    val node2 = RingNode(BigInt(100), "localhost", 8001, "localhost", 9001, "node")

    val ring = DistributedHashTable.createRing(List(node1, node2))

    val dht = testKit.spawn(DistributedHashTable(ring, 5))

    val list = result(dht.ask(GetTopN(250, 4, _: ActorRef[Option[Ring]])), 1.second)
    list should be(None)
  }
}
