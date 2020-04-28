package nl.tudelft.htable.core

/**
 * An action for (un)assigning a tablet to a node.
 */
case class AssignAction (tablet: Tablet, action: AssignType.Type)

