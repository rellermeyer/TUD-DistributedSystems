package hyperdex

import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

class Experiment1Search extends Simulation {

  val NUM_ATTRIBUTES = 5

  val indexFeeder = Iterator.from(1).map(i => Map("index" -> i))
  val valueFeeder = Iterator.from(1).map(i => Map("value" -> i))

  def generateTableString(numAttributes: Int): StringBody = {
    var generateTableString: String = "[\"attribute1\""
    for (attribute <- 2 to numAttributes) {
      generateTableString = generateTableString.concat(s""", "attribute$attribute"""")
    }
    generateTableString = generateTableString.concat("]")
    StringBody(generateTableString)
  }

  def generatePutString(numAttributes: Int): StringBody = {
    var putString: String = s"""{"attribute1" : ${1}"""
    for (attribute <- 2 to numAttributes) {
      putString = putString.concat(s""", "attribute$attribute" : """).concat("${value}")
    }
    putString = putString.concat("}")

    StringBody(putString)
  }

  def generateSearch(numAttributes: Int): HttpRequestBuilder = {
    val searchRecord = http("Search among ${n} * 50000 records")
      .get(url = "/search/table")
      .header("Content-Type", "application/json")
      .body(generatePutString(numAttributes))
      .check(status.is(200))
    searchRecord
  }

  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("createTable")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(generateTableString(NUM_ATTRIBUTES))
    .check(bodyString.is("Create successful"))

  val putRecord = feed(indexFeeder)
    .feed(valueFeeder)
    .exec(
      http("putRecord")
        .post(url = "/put/table/${index}") // n is provided by loop in the scenario
        .header("Content-Type", "application/json")
        .body(generatePutString(NUM_ATTRIBUTES))
        .check(bodyString.is("Put Succeeded"))
    )

  val scn = scenario("Experiment 1: Search")
            .exec(createTable)
            .repeat(10, "n") {
              exec(repeat(50000, "numRecords") {
              exec(putRecord)
            }).repeat(50) {
                exec(generateSearch(NUM_ATTRIBUTES))
          }
      }

  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpProtocol)

}
