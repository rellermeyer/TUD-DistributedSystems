package dynamodb.node

import java.lang.Exception

import akka.actor
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.cluster.VectorClock
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.Timeout
import dynamodb.node.DistributedHashTable.GetTopN
import dynamodb.node.ring.Ring
import dynamodb.node.ValueRepository.GetValueByKey

import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

object InternalClient {
  def apply(host: String, port: Int, numNodes: Int, numReadMinimum: Int, numWriteMinimum: Int, nodeName: String)(implicit valueRepository: ActorRef[ValueRepository.Command], dht: ActorRef[DistributedHashTable.Command]): Behavior[Command] = Behaviors.receive { (context, command) =>
    implicit val actorSystem: ActorSystem[Nothing] = context.system
    implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(classicActorSystem)
    implicit val timeout: Timeout = 5.seconds

    command match {
      case Put(value, replyTo) =>
        (for {
          internalValueOption <- retrieveValueFromRepository(value, nodeName)
          _ <- if (internalValueOption.isDefined && value.version < internalValueOption.get.version) Future.failed(new Exception("Version too old")) else Future.successful()
          internalValue = internalValueOption match {
            case Some(internalValue) => ValueRepository.Value(value.key, value.value, internalValue.version :+ nodeName)
            case None => ValueRepository.Value(value.key, value.value, new VectorClock(TreeMap(nodeName -> 0)))
          }
          otherNodes <- getTopNByKey(internalValue.key, numNodes)
          responses <- Future.sequence(otherNodes.map(node => putOtherNodes(internalValue, Uri.from("http", "", node.host, node.port, "/internal"), node.nodeName)))
        } yield if (responses.count(r => r.status == StatusCodes.OK) >= numWriteMinimum - 1) {
          replyTo ! OK
        } else {
          replyTo ! KO("Not enough writes")
        }).recover {
          case e: Exception => replyTo ! KO(e.getMessage)
        }
        Behaviors.same
      case Get(key, replyTo) =>
        (for {
          otherNodes <- getTopNByKey(key, numNodes)
          responses <- Future.sequence(otherNodes.map(n => getOtherNodes(key, Uri.from("http", "", n.host, n.port, "/internal/"), n.nodeName)))
          numFailedResponses = responses.count(_.isEmpty)
          versions = responses.flatten
        } yield if (numFailedResponses > numReadMinimum) {
          replyTo ! KO("Not enough reads")
        } else {
          replyTo ! ValueRes(checkVersion(versions.toList))
        }).recover {
          case e: Exception => replyTo ! KO(e.getMessage)
        }
        Behaviors.same
    }
  }

  def retrieveValueFromRepository(previousValue: ValueRepository.Value, nodeName: String)(implicit valueRepository: ActorRef[ValueRepository.Command], timeout: Timeout, scheduler: Scheduler): Future[Option[ValueRepository.Value]] =
    for {
      internalValueOption <- valueRepository.ask(GetValueByKey(previousValue.key, _: ActorRef[Option[ValueRepository.Value]]))
    } yield internalValueOption

  def getTopNByKey(key: String, numNodes: Int)(implicit dht: ActorRef[DistributedHashTable.Command], timeout: Timeout, scheduler: Scheduler): Future[Ring] =
    for {
      otherNodesOption <- dht.ask(GetTopN(DistributedHashTable.getHash(key), numNodes, _: ActorRef[Option[Ring]]))
    } yield otherNodesOption.getOrElse(throw new Exception("Error getting top N nodes"))

  /**
   * Send get request to server
   *
   * @param key     key of the value to get
   * @param address address of the server
   * @return Http Response
   */
  def getOtherNodes[T](key: String, address: Uri, nodeName: String)(implicit actorSystem: actor.ActorSystem, mat: Materializer): Future[Option[ValueRepository.Value]] = {
    import JsonSupport._
//    actorSystem.log.debug("Send internal get request to {}: id = {}", nodeName, key)
    try for {
      response <- Http().singleRequest(HttpRequest(uri = address + key))
      _ <- if (response.status != StatusCodes.OK) Future.failed(new Exception("Empty value")) else Future.successful()
      value <- Unmarshal(response).to[ValueRepository.Value]
    } yield Some(value) catch {
      case _: Exception => Future {
        None
      }
    }
  }

  /**
   * Filters the value with the latest version
   *
   * @param values values from which to filter
   * @return value with the largest vectorclock
   */
  def checkVersion(values: List[ValueRepository.Value]): ValueRepository.Value = {
    var result = values.head
    for (a <- values; b <- values) {
      if (a.version > b.version && result.version < a.version) {
        result = a
      }
    }
    result
  }

  /**
   *
   * @param value   value to write
   * @param address address of the server
   * @return
   */
  def putOtherNodes(value: ValueRepository.Value, address: Uri, nodeName: String)(implicit actorSystem: actor.ActorSystem): Future[HttpResponse] = {
    import JsonSupport._
    import spray.json._
//    actorSystem.log.debug("Send put request to {}: Value = {}", nodeName, value.toString)
    Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = address,
      entity = HttpEntity(`application/json`, value.toJson.compactPrint)
    ))
  }

  // Trait defining responses
  sealed trait Response

  sealed trait Command

  final case class ValueRes(value: ValueRepository.Value) extends Response

  final case class KO(reason: String) extends Response

  final case class Put(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command

  final case class Get(key: String, replyTo: ActorRef[Response]) extends Command

  case object OK extends Response

}
