package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.Address

case class ResultMessage(from: Address, to: Address, message: String) extends FruitarianMessage(MessageHeader(ResultMessage.MessageType, from, to)) {
  override def serializeBody(): String = message
}

case object ResultMessage {
  val MessageType = "RESULT_MESSAGE"
  def fromHeaderAndBody(header: MessageHeader, body: String): ResultMessage = {
    ResultMessage(header.from, header.to, body)
  }
}
