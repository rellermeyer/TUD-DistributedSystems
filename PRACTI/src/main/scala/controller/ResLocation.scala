package controller

import core.{Body, Node, VirtualNode}

case class ResLocation(objectId: String, path: List[VirtualNode], originator: VirtualNode, requestId: Int) extends Serializable {
  def send(): Unit = {
    path.head.logMessage("Receiving info: " + objectId + " is at location " + originator)

    path.head.sendToControllerAsync(this)
  }

  def sendBodyRequest(thisNode: Node): Unit = {
    originator.sendToCoreAsync(ReqBody(thisNode.getVirtualNode(), Body(thisNode.root, objectId)))
  }
}
