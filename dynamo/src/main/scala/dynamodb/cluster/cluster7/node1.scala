package dynamodb.cluster.cluster7

import akka.actor.typed.ActorSystem
import dynamodb.node.Node

object node1 {
  def main(args: Array[String]): Unit = {
    val node = nodes.head
    ActorSystem(Node(node, nodes, clusterConfig), node.name)
  }
}
