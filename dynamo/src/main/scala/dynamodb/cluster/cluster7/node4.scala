package dynamodb.cluster.cluster7

import akka.actor.typed.ActorSystem
import dynamodb.node.Node

object node4 {
  def main(args: Array[String]): Unit = {
    val node = nodes(3)
    ActorSystem(Node(node, nodes, clusterConfig), node.name)
  }
}
