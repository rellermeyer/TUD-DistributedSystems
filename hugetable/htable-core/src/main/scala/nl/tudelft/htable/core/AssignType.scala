package nl.tudelft.htable.core

/**
 * The possible actions to perform for an assignment.
 */
object AssignType extends Enumeration {
  type Type = Value
  val Add, Remove, Create, Delete, Clear = Value
}
