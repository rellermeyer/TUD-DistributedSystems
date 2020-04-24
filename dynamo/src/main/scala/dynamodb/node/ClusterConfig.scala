package dynamodb.node

case class ClusterConfig(numReplicas: Int, numWriteMinimum: Int, numReadMinimum: Int) {
  if (numReplicas < numWriteMinimum) throw new Exception("numReplicas cannot be smaller than numWriteMinimum")
  if (numReplicas < numReadMinimum) throw new Exception("numReplicas cannot be smaller than numReadMinimum")
  if (numWriteMinimum < numReadMinimum) throw new Exception("numWriteMinimum cannot be smaller than numReadMinimum")
}
