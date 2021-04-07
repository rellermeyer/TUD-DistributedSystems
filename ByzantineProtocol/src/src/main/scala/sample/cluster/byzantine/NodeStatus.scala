package sample.cluster.byzantine

import akka.actor.typed.ActorRef

class NodeStatus {
  var actorRef: ActorRef[Node.Event] = _
  var isValidated: Boolean = false
}

object NodeStatus {
  def apply(actorRef: ActorRef[Node.Event], isValidated: Boolean) = {
    val nodeStatus = new NodeStatus
    nodeStatus.actorRef = actorRef
    nodeStatus.isValidated = isValidated
  }
}
