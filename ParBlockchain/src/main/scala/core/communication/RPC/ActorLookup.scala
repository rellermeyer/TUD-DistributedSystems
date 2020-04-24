package core.communication.RPC

import akka.actor.ActorRef
import core.Node
import scala.collection.mutable

object ActorLookup {
  private val nodeMap: mutable.Map[String, ActorRef] = mutable.Map[String, ActorRef]()

  def addNode(node: Node, ref: ActorRef): Unit = {
    nodeMap.put(node.id, ref)
  }

  def lookupNode(id: String): Option[ActorRef] = {
    nodeMap.get(id)
  }

  def removeNode(id: String): Unit = {
    nodeMap.remove(id)
  }
}
