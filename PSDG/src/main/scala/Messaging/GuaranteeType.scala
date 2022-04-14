package Messaging

/**
 * All guarantee types are defined internally as a tuple of (ID, Type)
 */
object GuaranteeType extends Enumeration {
  type GuaranteeType = Value

  val NONE = Value(1, "None")
  val ACK = Value(2, "ACK")
}