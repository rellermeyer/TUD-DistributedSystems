import core.Node

object main extends App {
  val node1 = new Node(9010, "./node-files/1/", "localhost", 1)
  val node2 = new Node(9020, "./node-files/2/", "localhost", 2)
  val node3 = new Node(9030, "./node-files/3/", "localhost", 3)
  val node4 = new Node(9040, "./node-files/4/", "localhost", 4)

  node1.addNeighbour(node3.getVirtualNode())
  node1.addNeighbour(node4.getVirtualNode())
  node3.addNeighbour(node4.getVirtualNode())
  node3.addNeighbour(node2.getVirtualNode())

//  node1.controller.requestBody("file.txt")
}
