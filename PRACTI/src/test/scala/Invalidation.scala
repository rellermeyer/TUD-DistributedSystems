import core.{Node, VirtualNode}
import invalidationlog.InvalidationProcessor

class Invalidation extends TestBase {
  test("invaliationProcessor.process") {

    val node1 = new Node(9020, "./testing/1/", "localhost", 1)
    val node2 = new Node(9030, "./testing/2/", "localhost", 2)
    val node3 = new Node(9040, "./testing/3/", "localhost", 3)

    val processorNode1 = new InvalidationProcessor(node1.controller)

    node1.addNeighbour(node2.getVirtualNode())
    node2.addNeighbour(node3.getVirtualNode())

    //    node1.controller.requestBody("very/deep/file.txt")

    val objId = "data.txt"

    val maybeItem = node1.checkpoint.getById(objId)
    maybeItem match {
      case Some(value) => {
        node1.clock.sendStamp(value.body)
        processorNode1.processUpdate(objId)
      }
    }

    Thread.sleep(2000)

    val node2Item = node2.checkpoint.getById(objId)
    node2Item match {
      case Some(value) => assert(value.invalid == true)

    }
    val node3Item = node2.checkpoint.getById(objId)
    node3Item match {
      case Some(value) => assert(value.invalid == true)

    }
  }

}
