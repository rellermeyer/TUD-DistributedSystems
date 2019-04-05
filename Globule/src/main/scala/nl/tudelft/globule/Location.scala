package nl.tudelft.globule

import cats.effect.IO
import com.snowplowanalytics.maxmind.iplookups.IpLookups

object Location {

  def lookupIpLocation(ip: String): Option[Location] = {
    val filename = Configs.GLOBULE_DIR + "/GeoLite2-City.mmdb"

    // DEBUG HARDCODE LOCATIONS
    //    if(ip.equals("192.168.99.1")){
    //      return Some(new Location(52.3680,4.9036));
    //    }
    //    if(ip.equals("192.168.99.130")){
    //      return Some(new Location(52.3680,4.9036));
    //    }

    val result = (for {
      ipLookups <- IpLookups.createFromFilenames[IO](
        geoFile = Some(filename),
        ispFile = None,
        domainFile = None,
        connectionTypeFile = None,
        memCache = false,
        lruCacheSize = 20000
      )
      lookup <- ipLookups.performLookups(ip)
    } yield lookup).unsafeRunSync()

    result.ipLocation match {
      case Some(Right(loc)) =>
        Some(new Location(loc.latitude, loc.longitude))
      case _ =>
        // give default location when no location is found (Amsterdam)
        Some(new Location(52.3680, 4.9036))
    }
  }
}

class Location(val latitude: Double, val longitude: Double) extends Serializable {
  override def toString: String = "POINT (%f %f)" format(longitude, latitude)

  /* The distance between two coordinates, in kms.  */
  def distanceTo(other: Location): Double = {
    val lat1 = math.Pi / 180.0 * latitude
    val lon1 = math.Pi / 180.0 * longitude
    val lat2 = math.Pi / 180.0 * other.latitude
    val lon2 = math.Pi / 180.0 * other.longitude
    // Uses the haversine formula:
    val dlon = lon2 - lon1
    val dlat = lat2 - lat1
    val a = math.pow(math.sin(dlat / 2), 2) + math.cos(lat1) *
      math.cos(lat2) * math.pow(math.sin(dlon / 2), 2)
    val c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    val kms = 6367 * c
    kms
  }
}
