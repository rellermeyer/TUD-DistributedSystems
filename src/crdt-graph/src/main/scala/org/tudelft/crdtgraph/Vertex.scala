package org.tudelft.crdtgraph

import scala.collection.mutable._



class Vertex(name: String, initId: String) {
    var Arcs = HashMap[String, ArrayBuffer[String]]()
    var Uuids = ArrayBuffer[String]()

    Uuids += initId;

    //Adds new id of the vertex when adding it multiple times
    def addId(id: String) :Unit = {
        if(!Uuids.contains(id)){
            Uuids += id;
        }
    }

    //Removes a set of Ids from the vertex when synchronizing changes.
    def removeIds(ids: Seq[String]): Unit = {
        Uuids --= ids
    }

    //Checks if the vertex is empty
    def toBeRemoved():Boolean = {
        return Uuids.isEmpty && Arcs.isEmpty
    }

    //Adds an arc to the vertex. Multiple additions of the same arc will add multiple ids.
    def addArc(targetVertex: String, id: String): Unit = {
        if(!Arcs.contains(targetVertex)){
            Arcs(targetVertex) = ArrayBuffer()
        }
        Arcs(targetVertex) += id
    }

    //checks if the vertex is connected to another vertex. It does NOT check if that vertex exists.
    def isConnectedTo(targetVertex: String): Boolean = {
        return Arcs.contains(targetVertex) && Arcs(targetVertex).nonEmpty
    }

    //Returns a collection of ids for the given target vertex. If the arc doesn't exist it will return an empty collection.
    def getArcUuids(targetVertex: String): Seq[String] = {
        if(Arcs.contains(targetVertex)){
            return Arcs(targetVertex)
        }
        return ArrayBuffer[String]()
    }

    //Removes specified set of ids from the arc to the given target vertex.
    def removeArcs(targetVertex: String, arcUuids : Seq[String] ): Unit = {
        Arcs(targetVertex) --= arcUuids
        if(Arcs(targetVertex).isEmpty){
            Arcs.remove(targetVertex)
        }
    }
}

