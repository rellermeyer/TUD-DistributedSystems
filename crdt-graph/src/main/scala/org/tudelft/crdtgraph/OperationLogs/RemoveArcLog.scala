package org.tudelft.crdtgraph.OperationLogs

import org.tudelft.crdtgraph.OperationLogs.OperationType.OperationType

class RemoveArcLog(var sourceVertex: String, var targetVertex: String, var arcUuids: scala.collection.mutable.Seq[String]) extends OperationLog() {
  var opType: OperationType = OperationType.removeArc
}