package controller

import core.{Node, VirtualNode}
import helper.socketHelper

case class ReqFile(originator: VirtualNode, requestingNode: VirtualNode, objectId: String, receivingNode: VirtualNode,
                   var path: List[VirtualNode], requestId: Int) extends Serializable {

  assert(!receivingNode.isInstanceOf[Node])
  path = List(originator)

  def send(): Unit = {
    requestingNode.logMessage("Sending body request to " + receivingNode + " for " + objectId)
    receivingNode.sendToControllerAsync(this)
  }
}
