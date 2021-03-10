package org.tudelft.crdtgraph.OperationLogs

import org.tudelft.crdtgraph.OperationLogs.OperationType.OperationType

class AddArcLog(var sourceVertex: String, var targetVertex: String, var arcUuid: String) extends  OperationLog() {
  var opType: OperationType = OperationType.addArc
}
