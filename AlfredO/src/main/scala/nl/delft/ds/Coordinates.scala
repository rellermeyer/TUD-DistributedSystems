package nl.delft.ds

case class Coordinates(latitude:Double, longitude:Double) {

  val EARTH_RADIUS = 6371000

  def distance_to(other: Coordinates): Double = {
    val latDistance = Math.toRadians(this.latitude - other.latitude)
    val lngDistance = Math.toRadians(this.longitude - other.longitude)
    val sinLat = Math.sin(latDistance / 2)
    val sinLng = Math.sin(lngDistance / 2)
    val a = sinLat * sinLat +
      (Math.cos(Math.toRadians(this.latitude)) *
        Math.cos(Math.toRadians(other.latitude)) *
        sinLng * sinLng)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    EARTH_RADIUS * c
  }
}

object Coordinates