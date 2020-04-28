package nl.tudelft.htable.core

/**
 * An [Exception] that is thrown when a node is asked for a tablet that it does not serve.
 */
case class NotServingTabletException(message: String) extends Exception(message)
