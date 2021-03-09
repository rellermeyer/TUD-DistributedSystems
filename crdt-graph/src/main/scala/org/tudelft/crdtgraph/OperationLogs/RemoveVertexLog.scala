package org.tudelft.crdtgraph.OperationLogs

import scala.collection.mutable._

class RemoveVertexLog(name: String, arcs: HashMap[String, ArrayBuffer[String]], uuids: Seq[String]) extends  OperationLog(uuids) {
  var opType = OperationType.removeVertex
  var removedArcs: HashMap[String, ArrayBuffer[String]] = arcs
  var vertexName: String = name
}
