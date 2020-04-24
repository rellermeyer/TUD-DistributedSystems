package com.github.arucard21.globe.replicator.distributedobject

import java.net.URI
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

case class Registration(name: String, location: URI)

object CommunicationSubobject {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  def send_request(otherObjectUri : URI, method : String, parameter : Int): Boolean = {
    val requestContent = new RequestFromOtherObject(method, parameter)
    val requestContentJson : String = mapper.writeValueAsString(requestContent)
    val requestUri = Uri(otherObjectUri.toString).withPath(Path("/"))

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = requestUri,
      entity = requestContentJson
    ))
    val completionFuture = responseFuture.flatMap(response => {
        if (response.status == StatusCodes.OK) {
          println(s"""The request with method "$method" and parameter "$parameter" was handled correctly by the distributed object at: ${otherObjectUri.toString}""")
          Future.successful(true)
        }
        else {
          Future.failed(new IllegalStateException(s"""The request with method "$method" and parameter "$parameter" could not be handled by the distributed object at: ${otherObjectUri.toString}"""))
        }
    })
    Await.result(completionFuture, Duration.Inf)
  }

  def register(lookupServiceUri : URI, name : String, location : URI, onCompleteFunction : (Try[HttpResponse]) => Unit) = {
    val registration = new Registration(name, location)
    val registrationJson : String = mapper.writeValueAsString(registration)
    val registrationUri = Uri(lookupServiceUri.toString).withPath(Path("/register"))

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = registrationUri,
      entity = registrationJson
    ))
    responseFuture.onComplete(onCompleteFunction)
  }

  def lookup_locations(lookupServiceUri: URI, distributedObjectName : String) : Array[URI] = {
    val getIdFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = Uri(lookupServiceUri.toString).withPath(Path(s"/getId/$distributedObjectName"))
    ))
    val locationsFuture = getIdFuture.flatMap ( (getIdResponse) => {
      if (getIdResponse.status == StatusCodes.OK) {
        val objectIdString: String = Await.result(Unmarshal(getIdResponse).to[String], Duration.Inf)
        val objectId = UUID.fromString(objectIdString)
        val getLocationsFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
          method = HttpMethods.GET,
          uri = Uri(lookupServiceUri.toString).withPath(Path(s"/getLocations/${objectId.toString}"))
        ))
        getLocationsFuture.flatMap( (getLocationsResponse) => {
          if (getLocationsResponse.status == StatusCodes.OK) {
            val objectLocationsJson = Await.result(Unmarshal(getLocationsResponse).to[String], Duration.Inf)
            val objectLocationsList = mapper.readValue[Array[String]](objectLocationsJson)
            Future.successful(objectLocationsList.map(location => URI.create(location)))
          }
          else
            Future.failed(new IllegalStateException(s"""Could not retrieve the locations of the object with the ID "$objectId" on the lookup service at: $lookupServiceUri"""))
        })
      }
      else
        Future.failed(new IllegalStateException(s"""Could not retrieve the ID of the object with the name "$distributedObjectName" on the lookup service at: $lookupServiceUri"""))
    })
    Await.result(locationsFuture, Duration.Inf)
  }

  def acquire_lock(otherObjectUri : URI): Boolean = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(otherObjectUri.toString).withPath(Path("/acquireLock"))
    ))
    val acquireLockFuture = responseFuture.flatMap(response => {
      if (response.status == StatusCodes.OK) {
        println(s"""The lock from the distributed object at: ${otherObjectUri.toString} was acquired""")
        Future.successful(true)
      }
      else {
        if(response.status == StatusCodes.Conflict) {
          Future.failed(new IllegalStateException(s"""The distributed object at: ${otherObjectUri.toString} was already locked"""))
        }
        else {
          Future.failed(new IllegalStateException(s"""The lock from the distributed object at: ${otherObjectUri.toString} could not be acquired"""))
        }
      }
    })
    Await.result(acquireLockFuture, Duration.Inf)
  }

  def release_lock(otherObjectUri : URI): Boolean = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(otherObjectUri.toString).withPath(Path("/releaseLock"))
    ))
    val releaseLockFuture = responseFuture.flatMap(response => {
      if (response.status == StatusCodes.OK) {
        println(s"""The lock from the distributed object at: ${otherObjectUri.toString} was released""")
        Future.successful(true)
      }
      else {
        if(response.status == StatusCodes.Conflict) {
          Future.failed(new IllegalStateException(s"""The distributed object at: ${otherObjectUri.toString} already had its lock released"""))
        }
        else {
          Future.failed(new IllegalStateException(s"""The lock from the distributed object at: ${otherObjectUri.toString} could not be released"""))
        }
      }
    })
    Await.result(releaseLockFuture, Duration.Inf)
  }

}
