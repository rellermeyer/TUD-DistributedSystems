package com.github.arucard21.globe.replicator.lookupservice

import java.net.URI
import java.util.UUID

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, HttpApp, Route}
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.{JsonMappingException, ObjectMapper}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}

import scala.collection.mutable

case class Registration(name: String, location: URI)

object LSServer extends HttpApp {
  var testUuid: UUID = UUID.randomUUID()
  // Contains mapping from name to id
  var ids: mutable.Map[String, UUID] = mutable.Map[String, UUID]()
  // Contains mapping from id to list of locations
  var locations: mutable.Map[UUID, Array[URI]] = mutable.Map[UUID, Array[URI]]()
  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  override protected def routes: Route =
    Directives.concat(
      Directives.pathPrefix("getId" / Segment) { name =>
        Directives.get {
          Directives.complete{
            if(ids contains name){
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, ids(name).toString)
            }
            else{
              HttpResponse(StatusCodes.NotFound, entity="The distributed object with this name could not be found.")
            }
          }
        }
      },
      Directives.pathPrefix("getLocations" / Segment) { id =>
        Directives.get {
          Directives.complete{
            try{
              val objectId = UUID.fromString(id)
              if(locations contains objectId){
                val objectLocations = locations(objectId)
                HttpEntity(ContentTypes.`application/json`, mapper.writeValueAsString(objectLocations))
              }
              else{
                HttpResponse(StatusCodes.NotFound, entity="No locations for the provided id could be found")
              }
            }
            catch {
              case e: IllegalArgumentException => HttpResponse(StatusCodes.BadRequest, entity="The provided id is not a valid UUID: "+ e.getMessage)
            }
          }
        }
      },
      Directives.path("register") {
        Directives.post {
          Directives.decodeRequest {
            Directives.entity(as[String]) { registrationJson =>
              Directives.complete {
                try{
                  val registration = mapper.readValue[Registration](registrationJson)
                  // add a newly generated id for this new name and initialize the list of locations for it
                  if (!(ids contains registration.name)) {
                    val objectId = UUID.randomUUID()
                    ids += (registration.name -> objectId)
                    locations += (objectId -> Array())
                  }
                  // add the new location for this name
                  var objectLocations = locations(ids(registration.name))
                  objectLocations = objectLocations :+ registration.location
                  locations += (ids(registration.name) -> objectLocations)
                  HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This replica of the distributed object has been registered successfully")
                }
                catch {
                  case e: JsonProcessingException => HttpResponse(StatusCodes.BadRequest, entity="The provided registration JSON is not valid: "+ e.getMessage)
                  case e: JsonMappingException => HttpResponse(StatusCodes.BadRequest, entity="The provided registration JSON is not valid: "+ e.getMessage)
                }
              }
            }
          }
        }
      }
    )
}
