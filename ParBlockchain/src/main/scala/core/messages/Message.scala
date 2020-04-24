package core.messages

object MessageType extends Enumeration {
  type MessageType = Value
  val request, new_block, commit = Value
}

import MessageType._

abstract class Message(ty: MessageType, s: String, r: String) {
  val messageType: MessageType = ty
  val sender: String = s
  val receiver: String = r
}
