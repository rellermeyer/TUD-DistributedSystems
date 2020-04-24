package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.Address

case class EntryRequest(from: Address, to: Address, id: String) extends FruitarianMessage(MessageHeader(EntryRequest.MessageType, from, to)) {
  override def serializeBody(): String = id
}

case object EntryRequest {
  val MessageType = "ENTRY_REQUEST"

  def fromHeaderAndBody(header: MessageHeader, body: String): EntryRequest = {
    EntryRequest(header.from, header.to, body)
  }
}
