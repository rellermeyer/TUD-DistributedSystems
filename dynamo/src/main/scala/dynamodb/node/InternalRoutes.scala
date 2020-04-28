package dynamodb.node

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

class InternalRoutes(buildValueRepository: ActorRef[ValueRepository.Command], nodeName: String)(implicit system: ActorSystem[_]) {
  import JsonSupport._
  import akka.actor.typed.scaladsl.AskPattern._

  // asking someone requires a timeout and a scheduler, if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.seconds

  lazy val theInternalValueRoutes: Route =
    pathPrefix("internal") {
      concat(
        pathEnd {
            post {
              entity(as[ValueRepository.Value]) { job =>
//                system.log.debug("Received put request at internal server {}: value = {}", nodeName, job.toString)
                val operationPerformed: Future[ValueRepository.Response] =
                  buildValueRepository.ask(ValueRepository.AddValue(job, _))
                onSuccess(operationPerformed) {
                  case ValueRepository.OK => complete("Value added")
                  case ValueRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
                }
              }
            }
        },
        (get & path(Remaining)) { id =>
//          system.log.debug("Received get request at internal server {}: id = {}", nodeName, id)
          val maybeValue: Future[Option[ValueRepository.Value]] =
            buildValueRepository.ask(ValueRepository.GetValueByKey(id, _))
          rejectEmptyResponse {
            complete(maybeValue)
          }
        },
        path("resolve") {
          post {
            entity(as[ValueRepository.Value]) { job =>
              val operationPerformed: Future[ValueRepository.Response] =
                buildValueRepository.ask(ValueRepository.AddValue(job, _))
              onSuccess(operationPerformed) {
                case ValueRepository.OK => complete("Value added")
                case ValueRepository.KO(reason) => complete(StatusCodes.InternalServerError -> reason)
              }
            }
          }
        }
      )
    }
}
