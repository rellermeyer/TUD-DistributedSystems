package dynamodb.cluster.cluster3

import akka.actor.typed.ActorSystem
import dynamodb.node.Node

object node2 {
  def main(args: Array[String]): Unit = {
    val node = nodes(1)
    ActorSystem(Node(node, nodes, clusterConfig), node.name)
  }
}
