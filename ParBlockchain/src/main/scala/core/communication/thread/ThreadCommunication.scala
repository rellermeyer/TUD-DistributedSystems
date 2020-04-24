package core.communication.thread

import core.Node
import core.communication.CommunicationLayer
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.Future

class ThreadCommunication(node: Node) extends CommunicationLayer(node) {
  override def init(): Unit = {
    ObjectLookup.addNode(node)
  }

  override def run(): Unit = {
    while (!finished) {
      while (outgoingQueue.nonEmpty) {
        val msg = outgoingQueue.dequeue()
        val node = ObjectLookup.lookupNode(msg.receiver)
        node match {
          case Some(node) => Future { node.receiveMessage(msg) }
          case None =>
        }
      }
      Thread.sleep(waitTime)
    }
  }
}
