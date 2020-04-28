package dynamodb.node


object ring {
  type Ring = LazyList[RingNode]

  case class RingNode(position: BigInt, host: String, port: Int, externalHost: String, externalPort: Int, nodeName: String)
}

