package kelips

import scala.collection.mutable.ListBuffer

/**
  * Class representing the logBook of a node.
  * This is gossiped to a (partial) set of the affinity group view on each heartbeat.
  */
class LogBook(val gossipItemList: ListBuffer[GossipItem]) {
  val limit = 2     //The limit to how many heartbeats new events stay in the logBook

  /**
    * Add a log entry, about Data that was added to the softstate.
    */
  def addLog(data: Data): Unit = {
    val gossipItem = new GossipItem('a', data)
    gossipItemList += gossipItem
  }

  /**
    * Add a log entry about Data that was removed from the softstate.
    */
  def removeLog(data: Data): Unit = {
    data.heartBeatCounter
    val gossipItem = new GossipItem('r', data)
    gossipItemList += gossipItem
  }

  /**
    * Add a log entry about Data that was updated in the softstate.
    */
  def updateLog(data: Data): Unit = {
    val gossipItem = new GossipItem('u', data)
    // Delete older entries
    val dataWithLowerOrEqualsHeartBeat = gossipItemList.find(item =>
      item.data.isSameData(data)
      && item.data.heartBeatCounter <= data.heartBeatCounter)
    if (dataWithLowerOrEqualsHeartBeat isDefined)
      gossipItemList -= dataWithLowerOrEqualsHeartBeat.get
    gossipItemList += gossipItem
  }

  /**
    * Remove items from the logBook that have exceeded the limit.
    */
  def removePotentialItems(heartBeat: Int): Unit = {
    gossipItemList.foreach(item => {
      if (heartBeat - item.data.heartBeatCounter >= limit) {
        gossipItemList -= item
      }
    })
  }
}
