package hyperdex

import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.util.Random

class Experiment2 extends Simulation {

  def generateTableString(numAttributes: Int): StringBody = {
    var generateTableString: String = "[\"attribute1\""
    for(attribute <- 2 to numAttributes){
      generateTableString = generateTableString.concat(s""", "attribute$attribute"""")
    }
    generateTableString = generateTableString.concat("]")
    StringBody(generateTableString)
  }

  def generatePutString(numAttributes: Int): StringBody = {
    var putString: String = s"""{"attribute1" : ${Random.nextInt(8)}"""
    for(attribute <- 2 to numAttributes){
       putString = putString.concat(s""", "attribute$attribute" : ${Random.nextInt(8)}""")
    }
    putString = putString.concat("}")

    StringBody(putString)
  }

  def generateSearch(numAttributes: Int): HttpRequestBuilder = {
    val searchRecord = http(s"searchRecord$numAttributes")
      .get(url="/search/table")
      .header("Content-Type", "application/json")
      .body(generatePutString(numAttributes))
      .check(status is 200)
    searchRecord
  }

  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("createTable")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(generateTableString(8))
    .check(bodyString is "Create successful")

  val putRecord = http("putRecord")
    .post(url="/put/table/${n}") //n is provided by loop in the scenario
    .header("Content-Type", "application/json")
    .body(generatePutString(8))
    .check(bodyString is "Put Succeeded")


  val scn = scenario("SearchSimulation")
    .exec(createTable)
    .repeat(1000, "n"){
      exec(putRecord)
    }
    .repeat(50) {
      exec(generateSearch(8))
        .exec(generateSearch(7))
        .exec(generateSearch(6))
        .exec(generateSearch(5))
        .exec(generateSearch(4))
        .exec(generateSearch(3))
        .exec(generateSearch(2))
        .exec(generateSearch(1))
    }



  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpProtocol)

}
