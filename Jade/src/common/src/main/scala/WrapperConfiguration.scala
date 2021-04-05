/**
  * Type of all wrapper configurations.
  *
  * @param localPorts the ports this wrapper will listen on.
  * @param targetAddress the ip and port of the target wrapper.
  */
class WrapperConfiguration(val localPorts: List[String], val targetAddress: Option[Address] = None)

object WrapperConfiguration {
    val defaultIP = "0.0.0.0"
    val defaultPort = "1234"
}
