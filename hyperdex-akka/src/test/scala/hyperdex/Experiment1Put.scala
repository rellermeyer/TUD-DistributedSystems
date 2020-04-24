package hyperdex

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class Experiment1Put extends Simulation {

  val indexFeeder = Iterator.from(1).map(i => Map("index" -> i))
  val attributeFeeder = Iterator.continually(
    Map("attribute1" -> Random.nextInt(100).toString, "attribute2" -> Random.nextInt(100).toString)
  )

  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val createTable = http("post")
    .post("/create/table")
    .header("Content-Type", "application/json")
    .body(StringBody("[\"attribute1\", \"attribute2\"]"))

  val putRecord = feed(attributeFeeder)
    .exec(
      http("post")
        .post("/put/table/${index}")
        .header("Content-Type", "application/json")
        .body(StringBody("""{ "attribute1" : ${attribute1}, "attribute2" : ${attribute2} }"""))
        .check(status.is(200))
    )

  val putRecord2 = feed(attributeFeeder)
    .exec(
      http("Put after ${n} * 50000 records")
        .post("/put/table/${index}")
        .header("Content-Type", "application/json")
        .body(StringBody("""{ "attribute1" : ${attribute1}, "attribute2" : ${attribute2} }"""))
        .check(status.is(200))
    )

  val repetition = 50000

  val scn = scenario("Experiment: Put after x records")
    .exec(createTable)
    .repeat(10, "n") {
      exec(repeat(50000, "numRecords") {
        feed(indexFeeder).exec(putRecord)
      }).repeat(50) {
        exec(putRecord2)
      }
    }
  val users = 1

  setUp(
    scn.inject(atOnceUsers(users))
  ).protocols(httpProtocol)

}
