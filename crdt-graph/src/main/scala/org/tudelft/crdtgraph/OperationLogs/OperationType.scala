package org.tudelft.crdtgraph.OperationLogs


object OperationType extends Enumeration {
  type OperationType = Value
  val addVertex, addArc, removeVertex, removeArc = Value
}