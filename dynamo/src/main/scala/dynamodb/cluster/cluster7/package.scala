package dynamodb.cluster

import dynamodb.node.ClusterConfig
import dynamodb.node.mainObj.NodeConfig

package object cluster7 {
  val local = true
  val nodes = List(
    if (local) NodeConfig(BigInt("14"), "node1", "localhost", 8001, "localhost", 9001) else NodeConfig(BigInt("14"), "node1", "192.168.1.21", 8001, "192.168.1.21", 9001),
    if (local) NodeConfig(BigInt("28"), "node2", "localhost", 8002, "localhost", 9002) else NodeConfig(BigInt("28"), "node2", "192.168.1.22", 8002, "192.168.1.22", 9002),
    if (local) NodeConfig(BigInt("42"), "node3", "localhost", 8003, "localhost", 9003) else NodeConfig(BigInt("42"), "node3", "192.168.1.23", 8003, "192.168.1.23", 9003),
    if (local) NodeConfig(BigInt("56"), "node4", "localhost", 8004, "localhost", 9004) else NodeConfig(BigInt("56"), "node4", "192.168.1.24", 8004, "192.168.1.24", 9004),
    if (local) NodeConfig(BigInt("70"), "node5", "localhost", 8005, "localhost", 9005) else NodeConfig(BigInt("70"), "node5", "192.168.1.25", 8005, "192.168.1.25", 9005),
    if (local) NodeConfig(BigInt("84"), "node6", "localhost", 8006, "localhost", 9006) else NodeConfig(BigInt("84"), "node6", "192.168.1.26", 8006, "192.168.1.26", 9006),
    if (local) NodeConfig(BigInt("100"), "node7", "localhost", 8007, "localhost", 9007) else NodeConfig(BigInt("100"), "node7", "192.168.1.27", 8007, "192.168.1.27", 9007)
  )

  val clusterConfig = ClusterConfig(numReplicas = 6, numWriteMinimum = 6, numReadMinimum = 6)
}
