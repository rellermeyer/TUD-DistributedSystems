package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.MessageSerializer
import org.json4s.Formats

abstract class FruitarianMessage(val header: MessageHeader) {
  implicit val formats: Formats = MessageSerializer.messageSerializeFormats
  def serializeBody(): String
}
