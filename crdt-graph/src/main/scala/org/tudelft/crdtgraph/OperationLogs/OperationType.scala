package org.tudelft.crdtgraph.OperationLogs

import org.tudelft.crdtgraph.OperationLogs.OperationType.OperationType

object OperationType extends Enumeration {
  type OperationType = Value
  val addVertex, addArc, removeVertex, removeArc = Value
}