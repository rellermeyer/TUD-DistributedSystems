package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.{Address, MessageSerializer}
import org.json4s.Formats
import org.json4s.jackson.Serialization.{read, write}

case class TransmitMessage(from: Address, to: Address, message: (Int, List[Byte])) extends FruitarianMessage(MessageHeader(TransmitMessage.MessageType, from, to)) {
	override def serializeBody(): String = {
		write(message)
	}
}

case object TransmitMessage {
	val MessageType = "TRANSMIT_MESSAGE"
	implicit val formats: Formats = MessageSerializer.messageSerializeFormats

	def fromHeaderAndBody(header: MessageHeader, body: String): FruitarianMessage = {
		TransmitMessage(header.from, header.to, read[(Int, List[Byte])](body))
	}
}
