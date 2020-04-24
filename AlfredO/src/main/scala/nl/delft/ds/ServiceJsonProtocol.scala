package nl.delft.ds

import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

object ServiceJsonProtocol extends DefaultJsonProtocol{
  implicit val coordinatesFormat: RootJsonFormat[Coordinates] = jsonFormat(
    Coordinates.apply, "latitude", "longitude"
  )
  implicit val serviceFormat: JsonFormat[Service] = jsonFormat3(Service.apply)
}
