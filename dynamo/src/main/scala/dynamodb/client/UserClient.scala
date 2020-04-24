package dynamodb.client

import akka.actor
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.util.Timeout
import dynamodb.node.DistributedHashTable.{AddNode, GetTopN}
import dynamodb.node.ring.{Ring, RingNode}
import dynamodb.node._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object UserClient {

  sealed trait Response

  final case class ValueRes(value: ValueRepository.Value) extends Response
  case object OK extends Response
  case class KO(reason: String) extends Response

  sealed trait Command

  final case class Put(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command
  final case class Get(key: String, replyTo: ActorRef[Response]) extends Command
  final case class UserClientFailure(replyTo: ActorRef[Response], reason: String) extends Command
  final case class Retrieved(value: ValueRepository.Value, replyTo: ActorRef[Response]) extends Command
  final case class Putted(replyTo: ActorRef[Response]) extends Command



  def apply(nodes: List[mainObj.NodeConfig]): Behavior[Command] =
    Behaviors.setup { ctx =>
      val dht: ActorRef[DistributedHashTable.Command] = ctx.spawn(DistributedHashTable(), "DistributedHashTable")
      for (node <- nodes) {
        dht ! AddNode(RingNode(node.position, node.internalHost, node.internalPort, node.externalHost, node.externalPort, node.name), ctx.system.ignoreRef[DistributedHashTable.Response])
      }
      new UserClient(ctx, dht)
      // url: ("http://localhost:8001/values")
      // postData("""{"key": "myKey", "value": "myValue", "version": {}}""")
    }

  class UserClient(context: ActorContext[UserClient.Command], dht: ActorRef[DistributedHashTable.Command])
    extends AbstractBehavior[UserClient.Command](context) {

    import dynamodb.node.JsonSupport._
    import spray.json._

    implicit val actorSystem: ActorSystem[Nothing] = context.system
    implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
    implicit val materializer: Materializer = Materializer(classicActorSystem)
    implicit val executionContext: ExecutionContextExecutor = context.system.executionContext
    implicit val timeout: Timeout = 5.seconds

    /**
     * Send get request to server
     *
     * @param key key of the value to get
     * @return Http Response
     */
    def get(key: String): Future[ValueRepository.Value] = {
      val getNodeFuture = dht.ask(GetTopN(DistributedHashTable.getHash(key), 1, _: ActorRef[Option[Ring]]))
      val nodeFuture = getNodeFuture.map {
        case Some(top1) => top1.head
        case None => throw new Exception("Error getting Top N list")
      }
      val httpFuture = nodeFuture.flatMap(node => {
        val address = Uri.from("http", "", node.externalHost, node.externalPort, "/values/")
        val httpFuture = Http().singleRequest(HttpRequest(
          method = HttpMethods.GET,
          uri = address + key))
        httpFuture
      })
      val valueFuture = httpFuture.flatMap(res => res.status match {
        case StatusCodes.OK => Unmarshal(res).to[ValueRepository.Value]
        case _ => throw new Exception("Error with HTTP request for getting value: " + res.status + res.entity)
      })
      valueFuture
    }

    /**
     *
     * @param v value to write
     * @return
     */
    def put(v: ValueRepository.Value): Future[Boolean] = {
      val getNodeFuture = dht.ask(GetTopN(DistributedHashTable.getHash(v.key), 1, _: ActorRef[Option[Ring]]))
      val nodeFuture = getNodeFuture.map {
        case Some(top1) => top1.head
        case None => throw new Exception("Error getting Top N list")
      }
      val httpFuture = nodeFuture.flatMap(node => {
        val address = Uri.from("http", "", node.externalHost, node.externalPort, "/values")
        val httpFuture = Http().singleRequest(HttpRequest(
          method = HttpMethods.POST,
          uri = address,
          entity = HttpEntity(`application/json`, v.toJson.compactPrint)
        ))
        httpFuture
      })
      val resFuture = httpFuture.map(res => res.status match {
        case StatusCodes.OK => true
        case _ => throw new Exception("Put HTTP error: " + res.status + res.entity.toString)
      })
      resFuture
    }

    override def onMessage(msg: UserClient.Command): Behavior[UserClient.Command] = {
      msg match {
        case UserClientFailure(replyTo, reason) =>
          replyTo ! KO(reason)
          Behaviors.same
        case Putted(replyTo) =>
          replyTo ! OK
          Behaviors.same
        case Put(value, replyTo) =>
          context.pipeToSelf(put(value)) {
            case Success(true) => Putted(replyTo)
            case Success(false) => UserClientFailure(replyTo, "Not enough writes")
            case Failure(exception) => UserClientFailure(replyTo, exception.getMessage)
          }
          Behaviors.same
        case Retrieved(value, replyTo) =>
          replyTo ! ValueRes(value)
          Behaviors.same
        case Get(key, replyTo) =>
          context.pipeToSelf(get(key)) {
            case Success(value) => Retrieved(value, replyTo)
            case Failure(exception) => UserClientFailure(replyTo, exception.getMessage)
          }
          Behaviors.same
      }
    }
  }

}
