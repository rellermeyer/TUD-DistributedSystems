package main.scala.network

/**
 * Status code object for basic network communications
 */
object StatusCodes extends Enumeration {
  type statusCode = Value

//  All possible status codes
  val Heartbeat: StatusCodes.Value = Value
}
