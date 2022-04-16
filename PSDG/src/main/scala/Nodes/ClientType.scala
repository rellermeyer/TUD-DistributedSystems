package Nodes

/**
 * Define the client type as either Publisher or Subscriber.
 */
object ClientType extends Enumeration {

  type ClientType = Value
  val PUBLISHER, SUBSCRIBER = Value

}
