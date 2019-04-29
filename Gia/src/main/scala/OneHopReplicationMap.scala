package gia.core

import scala.collection.mutable

class OneHopReplicationMap {
  private val neighborToContent = new mutable.HashMap[GiaNode, mutable.Set[String]] with mutable.MultiMap[GiaNode, String]
  private val contentToNeighbor = new mutable.HashMap[String, mutable.Set[GiaNode]] with mutable.MultiMap[String, GiaNode]

  def getContent(neighbor: GiaNode): Option[mutable.Set[String]] = {
    neighborToContent.get(neighbor)
  }

  def getNodes(content: String): Option[mutable.Set[GiaNode]] = {
    contentToNeighbor.get(content)
  }

  def addContent(content: List[GiaFile], neighbor: GiaNode): Unit = {
    content.foreach(file => {
      neighborToContent.addBinding(neighbor, file.name)
      contentToNeighbor.addBinding(file.name, neighbor)
    })
  }

  def removeContent(neighbor: GiaNode): Unit = {
    val oldFiles = this.neighborToContent.remove(neighbor).orNull
    if (oldFiles != null) {
      oldFiles.map(filename => contentToNeighbor.removeBinding(filename, neighbor))
    }
  }
}
