package org.tudelft.crdtgraph.OperationLogs

import org.tudelft.crdtgraph.OperationLogs.OperationType.OperationType

import scala.collection.mutable._

class RemoveVertexLog(var vertexName: String, var arcUuids: HashMap[String, ArrayBuffer[String]], var vertexUuids: Seq[String]) extends  OperationLog() {
  var opType: OperationType = OperationType.removeVertex
}
