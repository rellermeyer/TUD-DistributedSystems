package dynamodb.cluster.cluster7

import akka.actor.typed.ActorSystem
import dynamodb.node.Node

object node5 {
  def main(args: Array[String]): Unit = {
    val node = nodes(4)
    ActorSystem(Node(node, nodes, clusterConfig), node.name)
  }
}
