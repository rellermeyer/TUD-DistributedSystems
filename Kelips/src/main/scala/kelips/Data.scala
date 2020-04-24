package kelips

trait Data {
  var heartBeatCounter: Int
  def isSameData(obj: Any): Boolean
}
