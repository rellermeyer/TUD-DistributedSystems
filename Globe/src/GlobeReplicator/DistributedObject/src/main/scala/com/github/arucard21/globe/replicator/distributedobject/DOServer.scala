package com.github.arucard21.globe.replicator.distributedobject

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, HttpApp, Route}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}

case class RequestFromOtherObject(method: String, parameter: Int)

object DOServer extends HttpApp {
  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  /*
  The API accepts the following requests:
  - / (root)
    - POST: handles requests from other objects
      returns 200 OK if successfully handled
  - /acquireLock
    - POST: acquire a lock from the replication subobject
      returns 200 OK if the lock was successfully acquired or 409 Conflict if it could not be acquired
  - /releaseLock
    - POST: release a lock from the replication subobject
      returns 200 OK if the lock was successfully released or 409 Conflict if it could not be released
  - /getNumber
    - GET: retrieve the number stored in the semantics object
      returns 200 OK with the retrieved number in the response body as plain/text
  - /setNumber/<number>
    - POST: set the number stored in the semantics object to <number>
      returns 200 OK if the number was successfully stored or 500 Internal Server Error if it could not be stored (possibly due to replication failure)
   */
  override protected def routes: Route =
    Directives.concat(
      Directives.pathSingleSlash {
        Directives.post {
          Directives.decodeRequest{
            Directives.entity(Directives.as[String]){ requestContent =>
              Directives.complete{
                val requestData = mapper.readValue[RequestFromOtherObject](requestContent)
                ControlSubobject.handle_request(requestData.method, requestData.parameter)
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Request handled successfully")
              }
            }
          }
        }
      },
      Directives.concat(
        Directives.path("acquireLock") {
          Directives.post{
            Directives.complete{
              if (ReplicationSubobject.acquireLock){
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Lock successfully acquired")
              }
              else{
                HttpResponse(StatusCodes.Conflict, entity = "The lock could not be acquired")
              }
            }
          }
        }
      ),
      Directives.concat(
        Directives.path("releaseLock") {
          Directives.post{
            Directives.complete{
              if (ReplicationSubobject.releaseLock){
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Lock successfully released")
              }
              else{
                HttpResponse(StatusCodes.Conflict, entity = "The lock could not be released")
              }
            }
          }
        }
      ),
      Directives.path("getNumber") {
        Directives.get {
          Directives.complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, ControlSubobject.getNumber.toString))
        }
      },
      Directives.pathPrefix("setNumber" / IntNumber) { newNumber =>
        Directives.post {
          Directives.complete{
            val prevNumber = ControlSubobject.getNumber
            if (prevNumber == newNumber){
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, "The number of this distributed object did not need to be updated. It was already set to " + prevNumber.toString)
            }
            else{
              ControlSubobject.setNumber(newNumber)
              val number = ControlSubobject.getNumber
                if (number == prevNumber){
                HttpResponse(StatusCodes.InternalServerError, entity = "The change could not be applied (it might not have been replicated correctly)")
              }
              else{
                HttpEntity(ContentTypes.`text/plain(UTF-8)`, "The number of this distributed object has been updated from " + prevNumber + " to " + number)
              }
            }
          }
        }
      }
    )
}
