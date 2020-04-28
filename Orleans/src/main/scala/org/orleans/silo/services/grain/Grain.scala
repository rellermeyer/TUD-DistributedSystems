package org.orleans.silo.services.grain

object Grain {
  type Receive = PartialFunction[Any, Unit]
}

abstract class Grain(val _id: String) extends Serializable {
  def receive: Grain.Receive
}
