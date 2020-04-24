package hyperdex

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.util.Random

class Experiment1Get extends Simulation {

  val indexFeeder: Iterator[Map[String, Int]] = Iterator.from(1).map(i => Map("index" -> i))
  val maxIndexFeeder = Iterator.continually(Map("randomIndex" -> Random.nextInt(10000).toString))
  val attributeFeeder: Iterator[Map[String, String]] = Iterator.continually(
    Map("attribute1" -> Random.nextInt(100).toString, "attribute2" -> Random.nextInt(100).toString)
  )

  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val putRecord = feed(indexFeeder)
    .feed(attributeFeeder)
    .exec(
      http("post")
        .post("/put/table/${index}")
        .header("Content-Type", "application/json")
        .body(StringBody("""{ "attribute1" : ${attribute1}, "attribute2" : ${attribute2} }"""))
        .check(status.is(200))
    )

  val createTable = http("post")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(StringBody("[\"attribute1\", \"attribute2\"]"))

  val getRecord = exec(
      http("Get after ${n} * 50000 records")
        .get("/get/table/${randomIndex}")
        .check(status.is(200))
        .check(bodyString.exists)
  )


  val scn = scenario(" Experiment Get")
    .exec(createTable)
    .repeat(10, "n") {
      exec(repeat(50000, "numRecords") {
        exec(putRecord)
      }).feed(maxIndexFeeder).repeat(50) {
        exec(getRecord)
      }
    }

  setUp(
    scn.inject(atOnceUsers(1))
  ).protocols(httpProtocol)

}
