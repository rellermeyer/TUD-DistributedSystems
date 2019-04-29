package clock

import core.Node

trait ClockInfluencer  extends Serializable {
  var timestamp : Long

  //  def sendStamp(n: Node): Unit = {
  //    timestamp = n.clock.time
  //    n.clock.sendStamp(this)
  //  }
  //
  //  def receiveStamp(n: Node): Unit = {
  //    n.clock.receiveStamp(this)
  //
  //  }
}
