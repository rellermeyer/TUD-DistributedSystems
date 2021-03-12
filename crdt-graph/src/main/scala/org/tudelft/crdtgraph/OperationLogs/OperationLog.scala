package org.tudelft.crdtgraph.OperationLogs

import org.tudelft.crdtgraph.OperationLogs.OperationType.OperationType


abstract class OperationLog() {
  var opType: OperationType
  var operationUuid = java.util.UUID.randomUUID.toString()
  //todo: discuss adding a timestamp for sync
}







