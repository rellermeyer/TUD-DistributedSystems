package org.tudelft.crdtgraph

import org.tudelft.crdtgraph.OperationLogs._

import java.util.concurrent.locks.ReentrantLock
import scala.collection.mutable._

object DataStore {
  //All operations within one instance have to be performed in linear succession.
  //Since this object is accessed by multiple threads from akka and from the synchronizer each public method has to lock.
  private val lock = new ReentrantLock()

  //Dictionary of vertices indexed by vertex name
  private val Vertices = new HashMap[String, Vertex]()

  //Collection of changes used to synchronize the state between instances.
  private val ChangesQueue = ArrayBuffer[OperationLog]()

  //Collection of ids of changes already applied to the dataset. Used in lookup in applyChanges.
  private val ChangesQueueIds = HashSet[String]()

  //Adds the specified vertex to the directed graph. Adding the same vertex multiple times will generate new UUIDs for it.
  def addVertex(vertexName: String): Boolean = {
    lock.lock()
    try {
      var newId = java.util.UUID.randomUUID.toString()
      var newChange = new OperationLog()
      newChange.AddVertexLog(vertexName, newId)

      applyAddVertex(newChange)
      return true
    }
    finally {
      lock.unlock()
    }
  }

  //Internal logic for adding a vertex
  private def applyAddVertex(cmd: OperationLog): Unit = {
    if (cmd.opType != OperationType.addVertex) {
      throw new IllegalArgumentException //coding error, should always receive addVertex
    }

    if (!Vertices.contains(cmd.vertexName)) {
      Vertices(cmd.vertexName) = new Vertex(cmd.vertexName, cmd.vertexUuid)
    }
    else {
      Vertices(cmd.vertexName).addId(cmd.vertexUuid)
    }
    addChange(cmd)
  }

  //Adds the specified arc to the graph. Adding the same arc multiple times will generate new UUIDs for it.
  //Operation will fail if the specified source is not present in the graph.
  def addArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    lock.lock()
    try {
      if (!doLookUpVertex(arcSourceVertex)) {
        return false //Cannot add an arc from an non-existing vertex
      }
      var newId = java.util.UUID.randomUUID.toString()
      var newChange = new OperationLog()
      newChange.AddArcLog(arcSourceVertex, arcTargetVertex, newId)

      applyAddArc(newChange)
      return true
    }
    finally {
      lock.unlock()
    }
  }

  //Internal logic for adding an arc
  private def applyAddArc(cmd: OperationLog): Unit = {
    if (cmd.opType != OperationType.addArc) {
      throw new IllegalArgumentException //coding error, should always receive addArc
    }
    if (!doLookUpVertex(cmd.sourceVertex)) {
      addChange(cmd)
      return //no source vertex, this is already check in addArc. If this is executed from synchronization removeVertex takes precedence
    }

    Vertices(cmd.sourceVertex).addArc(cmd.targetVertex, cmd.arcUuid)
    addChange(cmd)
  }

  //Removes the vertex form the graph. It will also remove all arcs starting in that vertex.
  //Operation will fail if that vertex is missing.
  def removeVertex(vertexName: String): Boolean = {
    lock.lock()
    try {
      if (!doLookUpVertex(vertexName)) {
        return false //Already removed - return false to the API
      }

      //remove the vertex
      var change = new OperationLog()
      change.RemoveVertexLog(vertexName, Vertices(vertexName).Uuids.clone)

      applyRemoveVertex(change)
      return true;
    }
    finally {
      lock.unlock()
    }
  }

  //Internal logic for removing an vertex
  private def applyRemoveVertex(cmd: OperationLog): Unit = {
    if (cmd.opType != OperationType.removeVertex) {
      throw new IllegalArgumentException //coding error, should always receive removeVertex
    }

    if (!doLookUpVertex(cmd.vertexName)) {
      addChange(cmd)
      return //Already removed / nothing to remove
    }

    //remove all the arcs
    Vertices(cmd.vertexName).Arcs.foreach(arcKeyValue => {
      var change = new OperationLog()
      change.RemoveArcLog(cmd.vertexName, arcKeyValue._1, arcKeyValue._2.clone)
      applyRemoveArc(change)
    })

    Vertices(cmd.vertexName).removeIds(cmd.vertexUuids)
    if (Vertices(cmd.vertexName).toBeRemoved()) {
      Vertices.remove(cmd.vertexName)
    }
    addChange(cmd)
  }

  //Removes an arc from the graph.
  //Operation will fail if that arc is not present in the graph
  def removeArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    lock.lock()
    try {
      if (!(doLookUpVertex(arcSourceVertex) && Vertices(arcSourceVertex).isConnectedTo(arcTargetVertex))) {
        return false //Already removed - return false to the API
      }

      val pastArcsUUIDs = Vertices(arcSourceVertex).getArcUuids(arcTargetVertex).clone
      var change = new OperationLog()
      change.RemoveArcLog(arcSourceVertex, arcTargetVertex, pastArcsUUIDs)

      applyRemoveArc(change)
      return true
    }
    finally {
      lock.unlock()
    }
  }

  //Internal logic for removing an arc.
  private def applyRemoveArc(cmd: OperationLog): Unit = {
    if (cmd.opType != OperationType.removeArc) {
      throw new IllegalArgumentException //coding error, should always receive removeVertex
    }
    if (!(doLookUpVertex(cmd.sourceVertex) && Vertices(cmd.sourceVertex).isConnectedTo(cmd.targetVertex))) {
      addChange(cmd)
      return //Already removed / nothing to remove
    }

    Vertices(cmd.sourceVertex).removeArcs(cmd.targetVertex, cmd.arcUuids)
    addChange(cmd)
  }

  //Executes the collection of changes received for another instance.
  def applyChanges(changes: Vector[OperationLog]): Boolean = {
    lock.lock()
    try {
      changes.foreach((changeLog: OperationLog) => {
        if (!ChangesQueueIds.contains(changeLog.operationUuid)) {
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
    finally {
      lock.unlock()
    }
  }

  //Checks if a given vertex is present in the graph.
  def lookUpVertex(vertexName: String): Boolean = {
    lock.lock()
    try {
      return doLookUpVertex(vertexName)
    }
    finally {
      lock.unlock()
    }
  }

  //Checks if a given arc is present in the graph.
  def lookUpArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    lock.lock()
    try {
      return doLookupArc(arcSourceVertex, arcTargetVertex)
    }
    finally {
      lock.unlock()
    }
  }

  //Checks if a given vertex is present in the graph (with no lock).
  private def doLookUpVertex(vertexName: String): Boolean = {
    return Vertices.contains(vertexName) && Vertices(vertexName).Uuids.nonEmpty
  }

  //Checks if a given arc is present in the graph (with no lock).
  private def doLookupArc(arcSourceVertex: String, arcTargetVertex: String): Boolean = {
    return doLookUpVertex(arcSourceVertex) && doLookUpVertex(arcTargetVertex) && Vertices(arcSourceVertex).isConnectedTo(arcTargetVertex)
  }

  //Returns a collection of changes contained in ChangesQueue. You can skip the first n changes.
  def getLastChanges(skip: Int): Seq[OperationLog] = {
    lock.lock()
    try {
      return ChangesQueue.drop(skip).clone()
    }
    finally {
      lock.unlock()
    }

  }

  //Internal. Adds a change to both collections.
  private def addChange(operation: OperationLog): Unit = {
    ChangesQueue += operation
    ChangesQueueIds += operation.operationUuid
  }

}
