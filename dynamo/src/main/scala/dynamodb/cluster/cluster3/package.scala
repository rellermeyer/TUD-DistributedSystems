package dynamodb.cluster

import dynamodb.node.ClusterConfig
import dynamodb.node.mainObj.NodeConfig

package object cluster3 {
  val local = true
  val nodes = List(
    if (local) NodeConfig(BigInt("33"), "node1", "192.168.1.165", 8001, "192.168.1.165", 9001) else NodeConfig(BigInt("33"), "node1", "192.168.1.21", 8001, "192.168.1.21", 9001),
    if (local) NodeConfig(BigInt("66"), "node2", "192.168.1.165", 8002, "192.168.1.165", 9002) else NodeConfig(BigInt("66"), "node2", "192.168.1.22", 8002, "192.168.1.22", 9002),
    if (local) NodeConfig(BigInt("100"), "node3", "192.168.1.165", 8003, "192.168.1.165", 9003) else NodeConfig(BigInt("100"), "node3", "192.168.1.23", 8003, "192.168.1.23", 9003)
  )
  val clusterConfig = ClusterConfig(numReplicas = 2, numWriteMinimum = 1, numReadMinimum = 1)
}
