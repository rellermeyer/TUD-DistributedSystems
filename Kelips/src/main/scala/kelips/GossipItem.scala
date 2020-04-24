package kelips

class GossipItem(val eventType: Char, val data: Data) {

  /**
    * Return true if the GossipItem's data object is the same data
    * @param obj
    * @return
    */
  def isSameData(obj: GossipItem): Boolean = {
    data.isSameData(obj)
  }

  /**
    * Return true if the object is exactly the same
    * @param obj
    * @return
    */
  override def equals(obj: Any): Boolean = {
    obj match {
      case gossipItem: GossipItem =>
        if (eventType == gossipItem.eventType
          && data.equals(gossipItem.data))
          true
        else false
      case _ => false
    }
  }
}
