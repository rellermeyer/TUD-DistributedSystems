package org.tudelft.crdtgraph.OperationLogs

class AddVertexLog(name: String, uuids: Seq[String]) extends  OperationLog(uuids) {
  var opType = OperationType.addVertex
  var vertexName: String = name
}
