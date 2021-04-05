package org.tudelft.crdtgraph.OperationLogs


object OperationType extends Enumeration {
  val addVertex = "addVertex"
  val addArc = "addArc"
  val removeVertex = "removeVertex"
  val removeArc = "removeArc"
}