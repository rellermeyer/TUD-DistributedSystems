package nl.delft.ds

import org.scalatest.funsuite.AnyFunSuite
import ServiceJsonProtocol._
import spray.json._

class ServiceRegistryTest extends AnyFunSuite{
  test("ServiceList Serializer") {
    val s: Service = Service("someName", "someURL", Coordinates(1.0, 0))
    val l: List[Service] = s :: Nil

    val json_serialized_data: String = l.toJson.compactPrint
    assert(
      json_serialized_data ==
      """[{"coordinates":{"latitude":1.0,"longitude":0.0},"name":"someName","url":"someURL"}]"""
    )
  }
}
