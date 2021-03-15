package org.tudelft.crdtgraph.OperationLogs

import scala.collection.mutable._

class OperationLog() {
  var opType: String = ""
  var operationUuid: String = java.util.UUID.randomUUID.toString()
  var timestamp: String = ""

  //The added or removed vertex. Present in 'addVertex' and 'removeVertex'
  var vertexName: String= ""
  //Id of the added vertex. Present only in 'addVertex'
  var vertexUuid: String= ""
  //Ids of the removed vertex. Present only in 'removeVertex'
  var vertexUuids: Seq[String]= new ArrayBuffer[String]()
  //The source vertex of the added or removed arc. Present in 'addArc' or 'removeArc'
  var sourceVertex: String= ""
  //The target vertex of the added or removed arc. Present in 'addArc' or 'removeArc'
  var targetVertex: String= ""
  //Id of the added arc. Present only in 'addArc'.
  var arcUuid: String= ""
  //Ids of the removed arcs. Present only in 'removeArc'.
  var arcUuids: Seq[String]= new ArrayBuffer[String]()

  def AddArcLog(sourceVertex: String, targetVertex: String, arcUuid: String) = {
    this.opType = OperationType.addArc
    this.sourceVertex= sourceVertex
    this.targetVertex= targetVertex
    this.arcUuid= arcUuid
  }

  def AddVertexLog(vertexName: String, vertexUuid: String) ={
    this.opType = OperationType.addVertex
    this.vertexName= vertexName
    this.vertexUuid= vertexUuid
  }

  def RemoveArcLog(sourceVertex: String, targetVertex: String, arcUuids: Seq[String]) ={
    this.opType = OperationType.removeArc
    this.sourceVertex= sourceVertex
    this.targetVertex= targetVertex
    this.arcUuids= arcUuids
  }

def RemoveVertexLog(vertexName: String, vertexUuids: Seq[String]) ={
    this.opType = OperationType.removeVertex
    this.vertexName= vertexName
    this.vertexUuids= vertexUuids
  }

}