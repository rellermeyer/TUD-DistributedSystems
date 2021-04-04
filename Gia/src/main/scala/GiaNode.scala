package gia.core

import akka.actor.ActorRef

@SerialVersionUID(1L)
class GiaNode(val actor: ActorRef, @volatile var capacity: Int, @volatile var degree: Int,
              @volatile var satisfaction: Float, @volatile var tokens: Int) extends Ordered[GiaNode] with Serializable {

  override def equals(obj: Any): Boolean = {
    if (obj == null || !obj.isInstanceOf[GiaNode]) {
      return false
    }

    val otherNode = obj.asInstanceOf[GiaNode]
    return otherNode.actor.equals(this.actor)
  }

  override def toString: String = {
    actor.path.address.toString
  }

  def compare(that: GiaNode): Int = {
    that.capacity.compareTo(this.capacity)
  }
}
