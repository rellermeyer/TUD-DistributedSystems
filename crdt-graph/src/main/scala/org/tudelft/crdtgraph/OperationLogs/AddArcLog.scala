package org.tudelft.crdtgraph.OperationLogs

class AddArcLog(source: String, target: String, uuids: Seq[String]) extends  OperationLog(uuids) {
  var opType = OperationType.addArc
  var sourceVertex: String = source
  var targetVertex: String = target
}
