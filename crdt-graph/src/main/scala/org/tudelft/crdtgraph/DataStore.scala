package org.tudelft.crdtgraph

import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable._

object DataStore {
  val Vertices = new HashMap[String, Vertex]()
  val ChangesQueue = ArrayBuffer[OperationLog]()


  def addVertex(vertexName: String): Boolean = {
    var newId = java.util.UUID.randomUUID.toString()
    var newChange = new AddVertexLog(vertexName, newId)

    applyAddVertex(newChange)
    return true
  }

  def applyAddVertex(cmd: AddVertexLog): Unit ={
    if(!Vertices.contains(cmd.vertexName)){
      Vertices(cmd.vertexName) = new Vertex(cmd.vertexName, cmd.vertexUuid)
    }
    else {
      Vertices(cmd.vertexName).addId(cmd.vertexUuid)
    }
    ChangesQueue += cmd
  }

  def addArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    var newId = java.util.UUID.randomUUID.toString()
    var newChange = new AddArcLog(arcSourceVertex, arcTargetVertex, newId)
    applyAddArc(newChange)
    return true
  }

  def applyAddArc(cmd: AddArcLog): Unit = {
    if(!lookUpVertex(cmd.sourceVertex)){
      throw new IllegalArgumentException //no source vertex
      //todo: discuss what should we do when this happens while synchronizing
    }
    Vertices(cmd.sourceVertex).addArc(cmd.targetVertex, cmd.arcUuid)
    ChangesQueue += cmd
  }

  def removeVertex(vertexName: String): Boolean = {
    if(!lookUpVertex(vertexName)){
      throw new IllegalArgumentException //to vertex to remove
      //todo: discuss what should we do when this happens while synchronizing
    }
    var arcs = Vertices(vertexName).Arcs
    var ids = Vertices(vertexName).Uuids
    var change = new RemoveVertexLog(vertexName, arcs, ids)
    applyRemoveVertex(change)
    return true;
  }

  def applyRemoveVertex(cmd : RemoveVertexLog):Unit = {
    if(!lookUpVertex(cmd.vertexName)){
      throw new IllegalArgumentException //to vertex to remove
      //todo: discuss what should we do when this happens while synchronizing
    }

    cmd.arcUuids.foreach( arcKeyValue =>{
      Vertices(cmd.vertexName).removeArcs(arcKeyValue._1, arcKeyValue._2)
    })
    Vertices(cmd.vertexName).removeIds(cmd.vertexUuids)
    if(Vertices(cmd.vertexName).toBeRemoved()){
      Vertices.remove(cmd.vertexName)
    }
    ChangesQueue += cmd
  }

  def removeArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    if(!lookUpArc(arcSourceVertex, arcTargetVertex)){
      throw new IllegalArgumentException //no arc to remove
      //todo: discuss what should we do when this happens while synchronizing
    }

    val pastArcsUUIDs = Vertices(arcSourceVertex).getArcUuids(arcSourceVertex)
    var change =  new RemoveArcLog(arcSourceVertex, arcTargetVertex, pastArcsUUIDs)
    applyRemoveArc(change)
    return true
  }

  def applyRemoveArc(cmd: RemoveArcLog):Unit = {
    if(!lookUpArc(cmd.sourceVertex, cmd.targetVertex)){
      throw new IllegalArgumentException //no arc to remove
      //todo: discuss what should we do when this happens while synchronizing
    }

    Vertices(cmd.sourceVertex).removeArcs(cmd.targetVertex, cmd.arcUuids)
    ChangesQueue += cmd
  }

  def applyChanges(changes: ArrayBuffer[OperationLog]):Boolean = {
    changes.foreach( (f: OperationLog) =>{
      if(!ChangesQueue.exists((x: OperationLog) => x.operationUuid == f.operationUuid)){
        f.opType match {
          case OperationType.addVertex => {
            var newVertex = f.asInstanceOf[AddVertexLog]
            applyAddVertex(newVertex)
          }
          case OperationType.addArc => {
            var newArc = f.asInstanceOf[AddArcLog]
            applyAddArc(newArc)
          }
          case OperationType.removeArc => {
            var removedArc = f.asInstanceOf[RemoveArcLog]
            applyRemoveArc(removedArc)
          }
          case OperationType.removeVertex => {
            var removedVertex = f.asInstanceOf[RemoveVertexLog]
            applyRemoveVertex(removedVertex)
          }
        }
      }
    })
    return true
  }

  def lookUpVertex(vertexName: String): Boolean = {
    return Vertices.contains(vertexName) && Vertices(vertexName).Uuids.nonEmpty
  }

  def lookUpArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    return Vertices.contains(arcSourceVertex) && Vertices(arcSourceVertex).isConnectedTo(arcTargetVertex)
  }

}
