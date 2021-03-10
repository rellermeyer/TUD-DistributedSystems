package org.tudelft.crdtgraph.OperationLogs

class AddVertexLog(var vertexName: String, var vertexUuid: String) extends  OperationLog() {
  var opType = OperationType.addVertex
}
