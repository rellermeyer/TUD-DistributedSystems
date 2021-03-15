package org.tudelft.crdtgraph

import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable._

object DataStore {
  val Vertices = new HashMap[String, Vertex]()
  val ChangesQueue = ArrayBuffer[OperationLog]()


  def addVertex(vertexName: String): Boolean = {
    var newId = java.util.UUID.randomUUID.toString()
    var newChange = new OperationLog()
    newChange.AddVertexLog(vertexName, newId)

    applyAddVertex(newChange)
    return true
  }

  def applyAddVertex(cmd: OperationLog): Unit ={
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
    var newChange = new OperationLog()
    newChange.AddArcLog(arcSourceVertex, arcTargetVertex, newId)

    applyAddArc(newChange)
    return true
  }

  def applyAddArc(cmd: OperationLog): Unit = {
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
    //remove all the arcs
    Vertices(vertexName).Arcs.foreach( arcKeyValue => {
      removeArc(vertexName, arcKeyValue._1)
    })

    //remove the vertex
    var change = new OperationLog()
    change.RemoveVertexLog(vertexName, Vertices(vertexName).Uuids.clone)

    applyRemoveVertex(change)
    return true;
  }

  def applyRemoveVertex(cmd : OperationLog):Unit = {
    if(!lookUpVertex(cmd.vertexName)){
      throw new IllegalArgumentException //to vertex to remove
      //todo: discuss what should we do when this happens while synchronizing
    }

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

    val pastArcsUUIDs = Vertices(arcSourceVertex).getArcUuids(arcTargetVertex).clone
    var change =  new OperationLog()
    change.RemoveArcLog(arcSourceVertex, arcTargetVertex, pastArcsUUIDs)

    applyRemoveArc(change)
    return true
  }

  def applyRemoveArc(cmd: OperationLog):Unit = {
    if(!lookUpArc(cmd.sourceVertex, cmd.targetVertex)){
      throw new IllegalArgumentException //no arc to remove
      //todo: discuss what should we do when this happens while synchronizing
    }

    Vertices(cmd.sourceVertex).removeArcs(cmd.targetVertex, cmd.arcUuids)
    ChangesQueue += cmd
  }

  def applyChanges(changes: Vector[OperationLog]):Boolean = {
    changes.foreach( (changeLog: OperationLog) =>{
      if(!ChangesQueue.exists((x: OperationLog) => x.operationUuid == changeLog.operationUuid)){
        changeLog.opType match {
          case OperationType.addVertex => {
            applyAddVertex(changeLog)
          }
          case OperationType.addArc => {
            applyAddArc(changeLog)
          }
          case OperationType.removeArc => {
            applyRemoveArc(changeLog)
          }
          case OperationType.removeVertex => {
            applyRemoveVertex(changeLog)
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
