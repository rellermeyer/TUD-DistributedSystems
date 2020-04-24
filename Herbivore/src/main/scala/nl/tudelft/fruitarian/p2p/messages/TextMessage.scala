package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.Address


case class TextMessage(from: Address, to: Address, message: String) extends FruitarianMessage(MessageHeader(TextMessage.MessageType, from, to)) {
  override def serializeBody(): String = message
}

case object TextMessage {
  val MessageType = "TEXT_MESSAGE"
  def fromHeaderAndBody(header: MessageHeader, body: String): TextMessage = {
    TextMessage(header.from, header.to, body)
  }
}