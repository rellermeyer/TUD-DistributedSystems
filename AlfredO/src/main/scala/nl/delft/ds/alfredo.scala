package nl.delft.ds

import org.scalatra.ScalatraServlet
import org.scalatra.scalate.ScalateSupport

import spray.json._
import ServiceJsonProtocol._

class alfredo extends ScalatraServlet with ScalateSupport{

  // private val google_maps_key: String = "j3ns6AqwSiRWb-XWtUU5wMovR6SAiR_YkWmgkO5Wrws"
  private val google_maps_key: String = "AIzaSyCOHS4spQNljbZNq3TBRYxjtxcjJ6s7eMc"

  get("/") {
    contentType = "text/html"
    ssp(
      "/index", "layout" -> "/layouts/default.ssp",
    "google_maps_api_key" -> this.google_maps_key,
    )
  }

  post("/services") {
    val latitude = params.get("latitude").get.toDouble
    val longitude = params.get("longitude").get.toDouble
    val coordinates = new Coordinates(latitude, longitude)
    val nearby_services = ServiceRegistry.get_nearby_services(coordinates)

    contentType = "application/json"
    nearby_services.toJson.compactPrint
  }
}
