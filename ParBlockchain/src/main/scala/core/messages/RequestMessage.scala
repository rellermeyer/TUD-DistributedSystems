package core.messages

import core.data_structures.Transaction

case class RequestMessage(transaction: Transaction, timestamp: Int, override val sender: String, override val receiver: String)
      extends Message(MessageType.request, sender, receiver)
