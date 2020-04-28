package dynamodb.node

import akka.actor.typed.ActorSystem

object mainObj {
  case class NodeConfig(position: BigInt, name: String, externalHost: String, externalPort: Int, internalHost: String, internalPort: Int)

  val nodes = List(
    NodeConfig(BigInt("0"), "node1", "localhost", 8001, "localhost", 9001),
    NodeConfig(BigInt("20"), "node2", "localhost", 8002, "localhost", 9002),
    NodeConfig(BigInt("50"), "node3", "localhost", 8003, "localhost", 9003),
    NodeConfig(BigInt("90"), "node4", "localhost", 8004, "localhost", 9004),
  )

  def main(args: Array[String]): Unit = {

    // name, node (external), port (external), host (internal), port (internal)
    // max range of MD5 is 2^128=340282366920938463463374607431768211456

    val clusterConfig = ClusterConfig(numReplicas = 3, numWriteMinimum = 2, numReadMinimum = 2)

    for (node <- nodes) {
      ActorSystem(Node(node, nodes, clusterConfig), node.name)
    }
  }
}
