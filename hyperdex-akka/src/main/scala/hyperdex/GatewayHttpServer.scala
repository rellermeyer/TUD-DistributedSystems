package hyperdex

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import hyperdex.MessageProtocol._
import sttp.tapir.server.akkahttp._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GatewayHttpServer {

  def run(host: String, port: Int, typedSystem: ActorSystem[GatewayMessage]): Unit = {

    implicit val ts: ActorSystem[GatewayMessage] = typedSystem
    implicit val untypedSystem: akka.actor.ActorSystem = ts.toClassic
    implicit val materializer: ActorMaterializer =
      ActorMaterializer()(untypedSystem)

    /**
      * routes
      */
    implicit val timeout: Timeout = 10.seconds

    def createRouteLogic(inp: API.Create.Input): Future[Either[API.ErrorInfo, String]] = {
      val createResult: Future[CreateResult] = typedSystem ? { ref =>
        Create(ref, inp.table, inp.attributes)
      }

      createResult
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(API.InternalError(exception.getMessage)))
          case Success(value) =>
            value.result match {
              case Left(_) =>
                Future.successful(
                  Left(API.InternalError("One of the datanodes did not respond, table was not created"))
                )
              case Right(_) =>
                Future.successful(Right("Create successful"))
            }
        }
    }

    def getRouteLogic(inp: API.Get.Input): Future[Either[API.ErrorInfo, Option[API.AttributeMapping]]] = {

      val lookupResult: Future[LookupResult] = typedSystem ? { ref =>
        Lookup(ref, inp.table, inp.key)
      }

      lookupResult
        .transformWith {
          case Success(value) =>
            Future.successful(transformLookupResponse(value))
          case Failure(exception) =>
            Future.successful(Left(API.InternalError(exception.getMessage)))
        }
    }

    def transformLookupResponse(response: LookupResult): Either[API.ErrorInfo, Option[API.AttributeMapping]] = {
      response.result match {
        case Left(lookupError) =>
          lookupError match {
            case TimeoutError       => Left(API.InternalError("internal timeout"))
            case TableNotExistError => Left(API.BadRequestError("No such table exists"))
          }
        case Right(optValue) => {
          // sadly have to check for null as None is serialized to null
          if (optValue == null)
            Right(None)
          else
            Right(optValue)
        }
      }
    }

    def putRouteLogic(inp: API.Put.Input): Future[Either[API.ErrorInfo, String]] = {

      val putResult: Future[PutResult] = typedSystem ? { ref =>
        Put(ref, inp.table, inp.key, inp.value)
      }

      putResult
        .transformWith {
          case Failure(exception) => Future.successful(Left(API.InternalError(exception.getMessage)))
          case Success(value)     => Future.successful(transformPutResponse(value))
        }
    }

    def transformPutResponse(response: PutResult): Either[API.ErrorInfo, String] = {
      response.result match {
        case Left(putError) =>
          putError match {
            case TimeoutError       => Left(API.InternalError("internal timeout"))
            case TableNotExistError => Left(API.BadRequestError("table does not exist"))
            case InvalidAttributeError(invalidAttributes) =>
              Left(API.BadRequestError(s"provided invalid attributes: $invalidAttributes"))
            case IncompleteAttributesError(missingAttributes) =>
              Left(API.BadRequestError(s"missing attributes: $missingAttributes"))
          }
        case Right(_) => Right("Put Succeeded")
      }
    }

    def searchRouteLogic(inp: API.Search.Input): Future[Either[API.ErrorInfo, Set[(API.Key, API.AttributeMapping)]]] = {
      val searchResult: Future[SearchResult] = typedSystem ? { ref =>
        Search(ref, inp.table, inp.query)
      }

      searchResult
        .transformWith {
          case Failure(exception) =>
            Future.successful(Left(API.InternalError(exception.getMessage)))
          case Success(value) =>
            Future.successful(transformSearchResponse(value))

        }
    }

    def transformSearchResponse(response: SearchResult): Either[API.ErrorInfo, Set[(API.Key, API.AttributeMapping)]] = {
      response.result match {
        case Left(searchError) =>
          searchError match {
            case TimeoutError       => Left(API.InternalError("internal timeout"))
            case TableNotExistError => Left(API.BadRequestError("No such table exists"))
            case InvalidAttributeError(invalidAttributes) =>
              Left(API.BadRequestError(s"Provided attributes do not exist: $invalidAttributes"))
          }
        case Right(value) =>
          val castedValue = value.map({ case (key, mapping) => (key.toInt, mapping) })
          Right(castedValue.toSet)
      }
    }

    val getRoute = API.Get.endp.toRoute(getRouteLogic)
    val putRoute = API.Put.endp.toRoute(putRouteLogic)
    val searchRoute = API.Search.endp.toRoute(searchRouteLogic)
    val createRoute = API.Create.endp.toRoute(createRouteLogic)
    val routes = {
      import akka.http.scaladsl.server.Directives._
      getRoute ~ putRoute ~ searchRoute ~ createRoute
    }

    val serverBinding: Future[Http.ServerBinding] =
      Http.apply().bindAndHandle(routes, host, port)

    println(s"Server online at http://localhost:8080/")
  }
}
