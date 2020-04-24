package kelips

import akka.actor.ActorRef

class GroupEntry(val actorRef: ActorRef, var heartBeatCounter: Int, var roundTripTime: Double, var heartBeatID: Int) extends Data{

  override def clone(): GroupEntry = {
    new GroupEntry(actorRef, heartBeatCounter, roundTripTime, heartBeatID)
  }

  override def isSameData(obj: Any): Boolean = {
    obj match {
      case otherGroupEntry: GroupEntry =>
        if (actorRef.equals(otherGroupEntry.actorRef)) true
        else false
      case _ => false
    }
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case otherGroupEntry: GroupEntry => {
        if (actorRef.equals(otherGroupEntry.actorRef)
          && heartBeatCounter == otherGroupEntry.heartBeatCounter
          && roundTripTime == otherGroupEntry.roundTripTime
          && heartBeatID == otherGroupEntry.heartBeatID)
          true
        else false
      }
      case _ => false
    }
  }
}
