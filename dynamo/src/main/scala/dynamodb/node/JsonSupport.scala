package dynamodb.node

import akka.cluster.VectorClock
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import scala.collection.immutable.TreeMap

object JsonSupport extends SprayJsonSupport {
  // import the default encoders for primitive types (Int, String, Lists etc)

  import spray.json._
  import DefaultJsonProtocol._
  import ValueRepository._

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue with Serializable = x match {
      case n: Long => JsNumber(n)
      case s: String => JsString(s)
      case b: Boolean if b => JsTrue
      case b: Boolean if !b => JsFalse
    }
    def read(value: JsValue): Any = value match {
      case JsNumber(n) => n.longValue
      case JsString(s) => s
      case JsTrue => true
      case JsFalse => false
    }
  }

  implicit def treeFormat[A: JsonFormat : Ordering, B: JsonFormat]: RootJsonFormat[TreeMap[A, B]] = new RootJsonFormat[TreeMap[A, B]] {
    //noinspection RedundantCollectionConversion
    override def write(obj: TreeMap[A, B]): JsValue = obj.toMap.toJson
    override def read(json: JsValue): TreeMap[A, B] = TreeMap.from(json.convertTo[Map[A, B]])
  }

  implicit object ClockFormat extends RootJsonFormat[VectorClock] {
    override def write(obj: VectorClock): JsValue = obj.versions.toJson
    override def read(json: JsValue): VectorClock = new VectorClock(json.convertTo[TreeMap[String, Long]])
  }

  implicit object ValueFormat extends RootJsonFormat[Value] {
    override def write(obj: Value): JsValue = JsObject(("key", obj.key.toJson), ("value", obj.value.toJson), ("version", obj.version.toJson))
    override def read(json: JsValue): Value = {
      val fields = json.asJsObject.fields
      val key = fields.getOrElse("key", throw new RuntimeException("Expected field \"key\" in Value")).convertTo[String]
      val value = fields.getOrElse("value", throw new RuntimeException("Expected field \"value\" in Value")).convertTo[String]
      val version = fields.getOrElse("version", null)

      if (version == null)
        Value(key, value)
      else
        Value(key, value, version.convertTo[VectorClock])
    }
  }
}
