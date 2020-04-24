package evaluation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import scala.util.Random;

class SpikeTestSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .inferHtmlResources()
    .acceptLanguageHeader("en-US,en;q=0.5")
    .userAgentHeader("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:76.0) Gecko/20100101 Firefox/76.0")

  val reqHeaders = Map(
    "Accept" -> "*/*",
    "Accept-Encoding" -> "gzip, deflate",
    "X-Requested-With" -> "XMLHttpRequest")

  // Bounding box of the Netherlands
  // Generated coordinates are somewhere in the bounding box
  val lat1 = 51.2412863967
  val lat2 = 53.6185799591
  val lon1 = 3.37153354
  val lon2 = 7.1947757275 
  val dlat = lat2 - lat1
  val dlon = lon2 - lon1

  val scn = scenario("Get services") // A scenario is a chain of requests and pauses
    .exec(http("services")
      .get(s"/services?latitude=${lat1 + Random.nextFloat() * dlat}&longitude=${lon1 + Random.nextFloat() * dlon}")
      .headers(reqHeaders))
  setUp(scn.inject(atOnceUsers(100)).protocols(httpProtocol))
}
