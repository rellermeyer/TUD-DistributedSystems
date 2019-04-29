import scala.collection.mutable.ListBuffer

class BayouRequest[D](val sender: String) extends Serializable {
  override def equals(obj: Any): Boolean = {
    obj match {
      case that: BayouRequest[D] => that.sender.equals(this.sender)
      case _ => false
    }
  }
}

class BayouReadRequest[D](sender: String) extends BayouRequest[D](sender) {
  def query(data: ListBuffer[D]): ListBuffer[D] = data
}

class BayouAntiEntropyRequest[D](sender: String, val tentativeStack: ListBuffer[StampedBayouWrite[D]], val committedStack: ListBuffer[StampedBayouWrite[D]]) extends BayouRequest[D](sender)

class BayouWriteRequest[D](sender: String) extends BayouRequest[D](sender) {
  def update(data: ListBuffer[D]): Unit = {}

  def dependencyCheck(data: ListBuffer[D]): Boolean = true

  def mergeProcedure(data: ListBuffer[D]): Unit = {}
}

class BayouResponse[D] extends Serializable

class BayouReadResponse[D](val data: ListBuffer[D]) extends BayouResponse[D] {
  override def toString: String = data.toString()
}

class BayouAntiEntropyResponse[D](val tentativeStack: ListBuffer[StampedBayouWrite[D]], val committedStack: ListBuffer[StampedBayouWrite[D]]) extends BayouResponse[D] {
  override def toString: String = s"{tentativeStack: $tentativeStack \n committedStack: $committedStack}"
}

class BayouWriteResponse[D](val success: Boolean) extends BayouResponse[D] {
  override def toString: String = success.toString()
}