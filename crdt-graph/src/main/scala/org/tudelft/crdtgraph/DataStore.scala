package org.tudelft.crdtgraph

import org.tudelft.crdtgraph.OperationLogs._

import scala.collection.mutable._

object DataStore {
  val Vertices = new HashMap[String, ArrayBuffer[String]]()
  val Arcs = new HashMap[String, HashMap[String, ArrayBuffer[String]]]()
  val ChangesQueue = ArrayBuffer[OperationLog]()

  def addVertex(vertexName: String): Boolean = {
    var newId = java.util.UUID.randomUUID.toString()
    var change = doAddVertex(vertexName, newId)
    ChangesQueue += change
    return true
  }

  def addArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    var newId = java.util.UUID.randomUUID.toString()
    var change = doAddArc(arcSourceVertex, arcTargetVertex, newId)
    ChangesQueue += change
    return true
  }

  def removeVertex(vertexName: String): Boolean = {
    var change = doRemoveVertex(vertexName)
    ChangesQueue += change
    return true;
  }

  def removeArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    var change = doRemoveArcs(arcSourceVertex, arcTargetVertex)
    ChangesQueue += change
    return true
  }

  def doAddVertex(vertexName: String, id: String): OperationLog = {
    if(!Vertices.contains(vertexName)){
      Vertices(vertexName) = ArrayBuffer()
    }
    Vertices(vertexName) += id
    return new AddVertexLog(vertexName, ArrayBuffer(id))
  }

  def doAddArc(arcSourceVertex: String, arcTargetVertex: String, id: String): AddArcLog = {
    if(!lookUpVertex(arcSourceVertex)){
      throw new IllegalArgumentException
    }

    if(!Arcs.contains(arcSourceVertex)){
      Arcs(arcSourceVertex) = HashMap[String, ArrayBuffer[String]]()
    }
    if(!Arcs(arcSourceVertex).contains(arcTargetVertex)){
      Arcs(arcSourceVertex)(arcTargetVertex) = ArrayBuffer()
    }
    Arcs(arcSourceVertex)(arcTargetVertex) += id
    return new AddArcLog(arcSourceVertex, arcTargetVertex, ArrayBuffer(id));
  }

  def doRemoveVertex(vertexName: String): RemoveVertexLog = {
    if(!lookUpVertex(vertexName)){
      throw new IllegalArgumentException
    }
    val pastVertexUUIDs = Vertices(vertexName)
    val pastArcsUUIDs = Arcs(vertexName)
    Vertices.remove(vertexName)
    Arcs.remove(vertexName)

    return new RemoveVertexLog(vertexName, pastArcsUUIDs, pastVertexUUIDs);
  }

  def doRemoveArcs(arcSourceVertex: String, arcTargetVertex: String): RemoveArcLog = {
    if(!lookUpArc(arcSourceVertex, arcTargetVertex)){
      throw new IllegalArgumentException
    }

    val pastArcsUUIDs = Arcs(arcSourceVertex)(arcTargetVertex)
    Arcs(arcSourceVertex).remove(arcTargetVertex)
    return new RemoveArcLog(arcSourceVertex, arcTargetVertex, pastArcsUUIDs)
  }

  def applyChanges(changes: ArrayBuffer[OperationLog]):Boolean = {
    changes.foreach( (f: OperationLog) =>{
      f.opType match {
        case OperationType.addVertex => {
          var newVertex = f.asInstanceOf[AddVertexLog]
          doAddVertex(newVertex.vertexName, newVertex.editedIds(0))
        }
        case OperationType.addArc => {
          var newArc = f.asInstanceOf[AddArcLog]
          try {
            doAddArc(newArc.sourceVertex, newArc.targetVertex, newArc.editedIds(0))
          } catch {
            case ex: IllegalArgumentException =>{
              println("Adding arc with no source in sync method")
            }
          }
        }
        case OperationType.removeArc => {
          var removedArc = f.asInstanceOf[RemoveArcLog]
          if(!lookUpArc(removedArc.sourceVertex, removedArc.targetVertex)){
            println("Removing a non existing arc")
            return false //TODO
          }
          Arcs(removedArc.sourceVertex)(removedArc.targetVertex).--=(removedArc.editedIds)
        }
        case OperationType.removeVertex => {
          var removedVertex = f.asInstanceOf[RemoveVertexLog]
          if(!lookUpVertex(removedVertex.vertexName)){
            println("Removing a non existing vertex")
            return false //TODO
          }

          Vertices(removedVertex.vertexName).--=(removedVertex.editedIds)
          if(!lookUpVertex(removedVertex.vertexName)){
            Vertices.remove(removedVertex.vertexName)
          }
          Arcs(removedVertex.vertexName).foreach( (arcKeyValue) =>{
            arcKeyValue._2.--=(removedVertex.removedArcs(arcKeyValue._1))
            if(!lookUpArc(removedVertex.vertexName, arcKeyValue._1)){
              Arcs(removedVertex.vertexName).remove(arcKeyValue._1)
            }
          })
          if(!Arcs(removedVertex.vertexName).nonEmpty){
            Arcs.remove(removedVertex.vertexName)
          }
        }
      }
    })
    return true
  }


  def dumpState(): String = {
    var finalResult = "";
    for ((k,v) <- Vertices) {
      finalResult+="v:";
      finalResult+=k;
      finalResult+="\n";
      println(v)
      for ( x <- v){
        println(x)
        finalResult+=x;
        finalResult+="\n";
      }
    }
    return finalResult;
  }

  def lookUpVertex(vertexName: String): Boolean = {
    return Vertices.contains(vertexName) && Vertices(vertexName).nonEmpty
  }

  def lookUpArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    return Arcs.contains(arcSourceVertex) &&
      Arcs(arcSourceVertex).contains(arcTargetVertex) &&
      Arcs(arcSourceVertex)(arcTargetVertex).nonEmpty
  }


}
