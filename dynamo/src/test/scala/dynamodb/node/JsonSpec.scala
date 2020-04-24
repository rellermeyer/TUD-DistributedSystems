package dynamodb.node

import akka.cluster.VectorClock
import dynamodb.node.JsonSupport._
import dynamodb.node.ValueRepository.Value
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._

import scala.collection.immutable.TreeMap

class JsonSpec extends AnyWordSpec with Matchers {
  "The Json Serializer" should {
    "serialize a value object" in {
      Value("mykey", "myvalue", new VectorClock()).toJson.compactPrint should be("""{"key":"mykey","value":"myvalue","version":{}}""")
    }

    "serialize a vector clock" in {
      new VectorClock(TreeMap("Node1" -> 1, "Node2" -> 2)).toJson.compactPrint should be("""{"Node1":1,"Node2":2}""")
    }
  }

  "The Json Deserializer" should {
    "deserialize a value object" in {
      """{"key":"mykey","value":"myvalue","version":{}}""".parseJson.convertTo[Value] should be(Value("mykey", "myvalue", new VectorClock()))
    }

    "deserialize a vector clock" in {
      """{"Node1":1,"Node2":2}""".parseJson.convertTo[VectorClock] should be(new VectorClock(TreeMap("Node1" -> 1, "Node2" -> 2)))
    }
  }
}
