package nl.tudelft.htable.core

import java.net.InetSocketAddress

/**
 * A node in a HTable cluster that serves nodes.
 */
final case class Node(uid: String, address: InetSocketAddress) {
  override def equals(that: Any): Boolean =
    that match {
      case that: Node => uid == that.uid
      case _          => false
    }

  override def hashCode: Int = uid.hashCode
}
