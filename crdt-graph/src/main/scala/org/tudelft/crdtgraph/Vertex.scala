package org.tudelft.crdtgraph

import scala.collection.mutable._

class Vertex(var name: String, initId: String) {
    var Arcs = HashMap[String, ArrayBuffer[String]]()
    var Uuids = ArrayBuffer[String]()

    Uuids += initId;

    def addId(id: String) :Unit = {
        if(!Uuids.contains(id)){
            Uuids += id;
        }
    }

    def addArc(targetVertex: String, id: String): Unit = {
        if(!Arcs.contains(targetVertex)){
            Arcs(targetVertex) = ArrayBuffer()
        }
        Arcs(targetVertex) += id
    }

    def isConnectedTo(targetVertex: String): Boolean = {
        return Arcs.contains(targetVertex) && Arcs(targetVertex).nonEmpty
    }

    def getArcUuids(targetVertex: String): Seq[String] = {
        if(Arcs.contains(targetVertex)){
            return Arcs(targetVertex)
        }
        return null // todo: not sure about this
    }

    def removeArcs(targetVertex: String, arcUuids : Seq[String] ): Unit = {
        Arcs(targetVertex) --= arcUuids
    }
}