package main.scala.network

/**
 * Address with IP/HOST and PORT
 * @param address - String in the form HOST:PORT
 */
class Address (val address: String) {
  private val splitAddress = address.split(':')
  if (splitAddress.length != 2) {
    throw new Error("Address is not correctly formatted: " + address)
  }
  val host: String = splitAddress(0)
  val port: Int = splitAddress(1).toInt

  override def toString: String = host + ':' + port.toString

}
