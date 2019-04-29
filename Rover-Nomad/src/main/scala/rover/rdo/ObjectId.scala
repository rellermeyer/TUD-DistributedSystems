package rover.rdo

case class ObjectId(asString: String) {
	override def toString: String = {
		return asString
	}

	override def equals(o: Any): Boolean = {
		o match {
			case string: String => this.asString == string
			case objectId: ObjectId => this.asString == objectId.asString
			case _ => false
		}
	}

	override def canEqual(that: Any): Boolean = {
		that match {
			case _: String => true
			case _: ObjectId => true
			case _ => false
		}
	}
}

object ObjectId {
	// FIXME: temporary during dev
	var chatAppChat: ObjectId = {
		from("chat")
	}

	def from(string: String): ObjectId = {
		return new ObjectId(string)
	}

	def generateNew(): ObjectId = {
//		val uuid = UUID.randomUUID()
		val uuid = "chat"
		return ObjectId(uuid.toString())
	}
}
