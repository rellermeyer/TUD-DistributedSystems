package org.tudelft.crdtgraph.OperationLogs

import java.util.Date
import scala.collection.mutable._

class OperationLog() {
  //The type of the operation. Possible values are defined in OperationType: addArc, addVertex, removeArc, removeVertex
  var opType: String = ""

  //The UUID of the operation. Used to recognize if that operation is already applied on a given instance.
  var operationUuid: String = java.util.UUID.randomUUID.toString()

  //Time at which operation was performed. Time is NOT synchronized and should NOT be used in conflict resolving.
  var timestamp: String = new Date(System.currentTimeMillis()).toString()

  //The added or removed vertex. Present in 'addVertex' and 'removeVertex'
  var vertexName: String = ""

  //Id of the added vertex. Present only in 'addVertex'
  var vertexUuid: String = ""

  //Ids of the removed vertex. Present only in 'removeVertex'
  var vertexUuids: Seq[String] = new ArrayBuffer[String]()

  //The source vertex of the added or removed arc. Present in 'addArc' or 'removeArc'
  var sourceVertex: String = ""

  //The target vertex of the added or removed arc. Present in 'addArc' or 'removeArc'
  var targetVertex: String = ""

  //Id of the added arc. Present only in 'addArc'.
  var arcUuid: String = ""

  //Ids of the removed arcs. Present only in 'removeArc'.
  var arcUuids: Seq[String] = new ArrayBuffer[String]()

  //Initializes the object to represent a addArc operation
  def AddArcLog(sourceVertex: String, targetVertex: String, arcUuid: String) = {
    this.opType = OperationType.addArc
    this.sourceVertex = sourceVertex
    this.targetVertex = targetVertex
    this.arcUuid = arcUuid
  }

  //Initializes the object to represent a addVertex operation
  def AddVertexLog(vertexName: String, vertexUuid: String) = {
    this.opType = OperationType.addVertex
    this.vertexName = vertexName
    this.vertexUuid = vertexUuid
  }

  //Initializes the object to represent a removeArc operation
  def RemoveArcLog(sourceVertex: String, targetVertex: String, arcUuids: Seq[String]) = {
    this.opType = OperationType.removeArc
    this.sourceVertex = sourceVertex
    this.targetVertex = targetVertex
    this.arcUuids = arcUuids
  }

  //Initializes the object to represent a removeVertex operation
  def RemoveVertexLog(vertexName: String, vertexUuids: Seq[String]) = {
    this.opType = OperationType.removeVertex
    this.vertexName = vertexName
    this.vertexUuids = vertexUuids
  }
}