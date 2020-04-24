package kelips

import akka.actor.ActorRef

class ContactNode(val actorRef: ActorRef, val groupID: Int, var heartBeatCounter: Int, var heartBeatID: Int) extends Data {

  override def isSameData(obj: Any): Boolean = {
    obj match {
      case otherContact: ContactNode => if (actorRef.equals(otherContact.actorRef)) true
        else false
      case _ => false
    }
  }

  override def clone(): ContactNode = {
    new ContactNode(actorRef, groupID, heartBeatCounter, heartBeatID)
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case otherContactNode: ContactNode => if (actorRef.equals(otherContactNode.actorRef)
        && groupID == otherContactNode.groupID
        && heartBeatCounter == otherContactNode.heartBeatCounter
        && heartBeatID == otherContactNode.heartBeatID)
        true
      else false
      case _ => false
    }
  }
}
