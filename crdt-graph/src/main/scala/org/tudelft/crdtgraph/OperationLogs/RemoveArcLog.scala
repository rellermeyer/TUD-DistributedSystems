package org.tudelft.crdtgraph.OperationLogs

class RemoveArcLog(source: String, target: String, uuids: Seq[String]) extends  OperationLog(uuids) {
  var opType = OperationType.removeArc
  var sourceVertex: String = source
  var targetVertex: String = target
}