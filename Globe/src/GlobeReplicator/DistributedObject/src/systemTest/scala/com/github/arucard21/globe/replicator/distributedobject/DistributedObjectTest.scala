package com.github.arucard21.globe.replicator.distributedobject

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}
import java.util.Arrays
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.scalatest.funsuite.AnyFunSuite
import org.junit.runner.RunWith
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfter, Ignore}
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.Random

/**
 * These tests require the Lookup Service to be running and its URL provided as a property with the name "lookupservice.url".
 * There also need to be at least 2 Distributed Object instances running, both of which should be registered with the Lookup Service.
 */
@RunWith(classOf[JUnitRunner])
class DistributedObjectTest extends AnyFunSuite with BeforeAndAfter {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val distributedObjectName = "test"
  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  var objectLocations: Array[Uri] = Array[Uri]()

  var lookupServiceUri = Uri(DOApplication.getLookupServiceUri.toString)

  before {
    objectLocations = Await.result(findLocationsForDistributedObject, Duration.Inf)
    assert(!objectLocations.isEmpty, "The locations were not retrieved correctly")
    assert(objectLocations.size >= 2, "There are less than 2 locations available for this distributed object. The tests require at least 2 locations.")
  }

  after{
    // reset the number for all objects
    objectLocations.foreach(location => setNumberForDistributedObject(location, 0))
  }

  private def findLocationsForDistributedObject= {
    val getIdFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = lookupServiceUri.withPath(Path(s"/getId/$distributedObjectName"))
    ))
    getIdFuture.flatMap ( (getIdResponse) => {
        if (getIdResponse.status == StatusCodes.OK) {
          val objectIdString: String = Await.result(Unmarshal(getIdResponse).to[String], Duration.Inf)
          val objectId = UUID.fromString(objectIdString)
          val getLocationsFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
            method = HttpMethods.GET,
            uri = lookupServiceUri.withPath(Path(s"/getLocations/${objectId.toString}"))
          ))
          getLocationsFuture.flatMap( (getLocationsResponse) => {
              if (getLocationsResponse.status == StatusCodes.OK) {
                val objectLocationsJson = Await.result(Unmarshal(getLocationsResponse).to[String], Duration.Inf)
                val objectLocationsList = mapper.readValue[Array[String]](objectLocationsJson)
                Future.successful(objectLocationsList.map(location => Uri(location)))
              }
              else
                Future.failed(new IllegalStateException(s"""Could not retrieve the locations of the object with the ID "$objectId" on the lookup service at: $lookupServiceUri"""))
            })
        }
        else
          Future.failed(new IllegalStateException(s"""Could not retrieve the ID of the object with the name "$distributedObjectName" on the lookup service at: $lookupServiceUri"""))
    })
  }

  def getNumberForDistributedObject(distributedObjectUri : Uri): Int ={
    val getNumberFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.GET,
      uri = distributedObjectUri.withPath(Path("/getNumber"))
    ))
    Await.result(getNumberFuture.flatMap((response) => Unmarshal(response).to[String]), Duration.Inf).toInt
  }

  def setNumberForDistributedObject(distributedObjectUri : Uri, newNumber : Int) ={
    val setNumberFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(
      method = HttpMethods.POST,
      uri = distributedObjectUri.withPath(Path(s"/setNumber/$newNumber"))
    ))
    Await.result(setNumberFuture.flatMap((response) => {
      if (response.status == StatusCodes.OK)
        Future.successful(true)
      else
        Future.failed(new IllegalStateException("Failed to set the new number on the distributed object"))
    }), Duration.Inf)
  }

  test("getNumber should correctly retrieve the number of the distributed object") {
    setNumberForDistributedObject(objectLocations(0), 12)
    val beginTime = System.currentTimeMillis()
    val retrievedNumber = getNumberForDistributedObject(objectLocations(0))
    val endTime = System.currentTimeMillis()
    assertResult(12, "The number in the distributed object does not match the default value") {
      retrievedNumber
    }
    val responseTimeInMillis = endTime - beginTime
    val filename = "nonReplicatedResponseTimes.csv"
    val file = Paths.get(filename)
    if(Files.notExists(file)){
      // write header row
      Files.write(Paths.get(filename), Arrays.asList("objectCount, responseTime(ms)"), StandardCharsets.UTF_8)
    }
    Files.write(Paths.get(filename), Arrays.asList(s"${objectLocations.size.toString}, $responseTimeInMillis"), StandardCharsets.UTF_8, StandardOpenOption.APPEND)
  }

  test("setNumber should correctly replicate to all other objects (scalability evaluation test)") {
    val newNumber : Int = Random.nextInt(1000)
    val beginTime = System.currentTimeMillis()
    val setNumberResult = setNumberForDistributedObject(objectLocations(0), newNumber)
    val endTime = System.currentTimeMillis()
    if (setNumberResult){
      objectLocations.foreach(location => {
        assertResult(newNumber, s"The object at $location does not contain the new number"){
          getNumberForDistributedObject(location)
        }
      })
    }
    val responseTimeInMillis = endTime - beginTime
    val filename = "responseTimesScalability.csv"
    val file = Paths.get(filename)
    if(Files.notExists(file)){
      // write header row
      Files.write(Paths.get(filename), Arrays.asList("objectCount, responseTime(ms)"), StandardCharsets.UTF_8)
    }
    Files.write(Paths.get(filename), Arrays.asList(s"${objectLocations.size.toString}, $responseTimeInMillis"), StandardCharsets.UTF_8, StandardOpenOption.APPEND)
  }

  test("setNumber on 2 local objects in the same distributed object concurrently should fail (concurrency evaluation test)") {
    val testedLocations = objectLocations.slice(0, 2)
    val beginTime = System.currentTimeMillis()
    val responses = testedLocations
      .map(objectLocation => {
        Http().singleRequest(HttpRequest(
          method = HttpMethods.POST,
          uri = objectLocation.withPath(Path(s"/setNumber/${Random.nextInt(1000)}"))
        ))
      })
      .map(responseFuture => Await.result(responseFuture, Duration.Inf))
    val endTime = System.currentTimeMillis()
    assert(responses.filter(response => response.status == StatusCodes.OK).size <= 1, "The locking process did not work correctly since more than one of the concurrent requests succeeded")

    val responseTimeInMillis = endTime - beginTime
    val filename = "responseTimesConcurrency.csv"
    val file = Paths.get(filename)
    if(Files.notExists(file)){
      // write header row
      Files.write(Paths.get(filename), Arrays.asList("objectCount, responseTime(ms)"), StandardCharsets.UTF_8)
    }
    Files.write(Paths.get(filename), Arrays.asList(s"${objectLocations.size.toString}, $responseTimeInMillis"), StandardCharsets.UTF_8, StandardOpenOption.APPEND)
  }

  test("setNumber 2 times on the same local object in a distributed object synchronously, one after the other, should correctly replicate changes to all other objects for both requests") {
    val newNumber : Int = Random.nextInt(1000)
    setNumberForDistributedObject(objectLocations(0), newNumber)
    val newNumber2 : Int = Random.nextInt(1000)
    if (setNumberForDistributedObject(objectLocations(0), newNumber2)){
      objectLocations.foreach(location => {
        assertResult(newNumber2, s"The object at $location does not contain the new number"){
          getNumberForDistributedObject(location)
        }
      })
    }
  }
}
