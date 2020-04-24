package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.Address

case class NextRoundMessage(from: Address, to: Address, roundId: Int) extends FruitarianMessage(MessageHeader(NextRoundMessage.MessageType,from, to)) {
  override def serializeBody(): String = roundId.toString
}

case object NextRoundMessage {
  val MessageType = "NEXT_ROUND"

  def fromHeaderAndBody(header: MessageHeader, body: String): NextRoundMessage = {
    NextRoundMessage(header.from, header.to, body.toInt)
  }
}
