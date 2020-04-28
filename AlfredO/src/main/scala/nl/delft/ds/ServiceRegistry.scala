package nl.delft.ds

import scala.collection.mutable.ListBuffer

object ServiceRegistry {

  var services: List[Service] = List()

  def loadServicesFromFile(path: String) = {
    val servicesBuffer: ListBuffer[Service] = ListBuffer()
    val lines = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path)).getLines
    lines.foreach(line => {
      val line = lines.next
      val Array(name, url, lat, lon) = line.split(",")
      servicesBuffer += Service(name, url, Coordinates(lat.toDouble, lon.toDouble))
    })
    this.services = servicesBuffer.toList
  }

  def get_nearby_services(coordinates: Coordinates):List[Service] = {
    // filter for all services within 5000 meters.
    this.services.filter(_.coordinates.distance_to(coordinates) < 50000000)
  }

}
