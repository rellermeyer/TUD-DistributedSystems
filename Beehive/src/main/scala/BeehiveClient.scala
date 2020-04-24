package Beehive

import com.twitter.finagle.http.{Request, RequestBuilder, Response}
import com.twitter.finagle.{Service, http}
import com.twitter.util.Future

import scala.collection.immutable
import scala.collection.mutable.HashMap

class BeehiveClient(otherNodes: HashMap[Int, Service[Request, Response]]) {
  val others = otherNodes

  def lookup(key: Int, id: Int, fromId: Int, hopsDone: Int): Future[Response] = {
    val params = Map(
      "event" -> "lookup",
      "fromId" -> fromId.toString,
      "key" -> Integer.toString(key),
      "hops" -> Integer.toString(hopsDone),
    )

    doRequest(id, params)
  }

  def update(id: Int, set: String): Future[Response] = {
    val params = Map(
      "event" -> "update",
      "dataItems" -> set
    )

    doRequest(id, params)
  }

  def remove(id: Int, set: String): Future[Response] = {
    val params = Map(
      "event" -> "remove",
      "dataItems" -> set
    )

    doRequest(id, params)
  }

  def aggregateUp(id: Int, fromId: Int, set: String): Future[Response] = {
    val params = Map(
      "event" -> "aggregateup",
      "fromId" -> fromId.toString,
      "dataItems" -> set
    )

    doRequest(id, params)
  }

  def getAggregate(id: Int, fromId: Int, set: String): Future[Response] = {
    val params = Map(
      "event" -> "getaggregate",
      "fromId" -> fromId.toString,
      "dataItems" -> set
    )

    doRequest(id, params)
  }

  def disseminate(id: Int, fromId: Int, set: String): Future[Response] = {
    val params = Map(
      "event" -> "disseminate",
      "fromId" -> fromId.toString,
      "dataItems" -> set
    )

    doRequest(id, params)
  }

  def doRequest(id: Int, params: immutable.Map[String, String]): Future[Response] = {
    val client: Service[Request, Response] = others(id)
    val request : http.Request = RequestBuilder()
      .url(http.Request.queryString(s"http://localhost:/${id}", params))
      .buildGet()
    client(request)
  }
}