package kelips

import akka.actor.ActorRef

class FileTuple(val fileName: String, val homeNode: ActorRef, var heartBeatCounter: Int, var heartBeatID: Int) extends Data {

  override def clone(): FileTuple = {
    new FileTuple(fileName, homeNode, heartBeatCounter, heartBeatID)
  }

  override def isSameData(obj: Any): Boolean = {
    obj match {
      case otherFileTuple: FileTuple =>
        if (fileName.equals(otherFileTuple.fileName)
          && homeNode.equals(otherFileTuple.homeNode))
          true
        else false
      case _ => false
    }
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case fileTuple: FileTuple => {
        if (fileName.equals(fileTuple.fileName)
          && homeNode.equals(fileTuple.homeNode)
          && heartBeatCounter == fileTuple.heartBeatCounter
          && heartBeatID == fileTuple.heartBeatID)
          true
        else false
      }
      case _ => false
    }
  }
}
