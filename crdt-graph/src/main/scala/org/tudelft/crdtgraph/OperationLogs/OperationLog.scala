package org.tudelft.crdtgraph.OperationLogs



abstract class OperationLog(uuids: Seq[String]) {
  var opType: OperationType
  var editedIds: Seq[String] = uuids
}







