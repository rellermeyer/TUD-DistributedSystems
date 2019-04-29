package clock

class Clock extends Serializable {
  var time :Long = 0

  def sendStamp(obj : ClockInfluencer): Long = {
    time += 1
    obj.timestamp = time

    time
  }

  def receiveStamp(obj : ClockInfluencer): Unit = {
    time = Math.max(obj.timestamp, time)
  }
}
