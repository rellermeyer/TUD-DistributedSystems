package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.{Address, MessageSerializer}
import org.json4s.Formats
import org.json4s.jackson.Serialization.{read, write}

case class AnnounceMessage(from: Address, to: Address, body: AnnounceMessageBody) extends FruitarianMessage(MessageHeader(AnnounceMessage.MessageType, from, to)) {
	override def serializeBody(): String = {
		write(body)
	}
}

case class AnnounceMessageBody(seed: String, id: String)

case object AnnounceMessage {
	val MessageType = "ANNOUNCE_MESSAGE"
	implicit val formats: Formats = MessageSerializer.messageSerializeFormats

	def fromHeaderAndBody(header: MessageHeader, body: String): AnnounceMessage = {
		val parsedBody = read[AnnounceMessageBody](body)
		AnnounceMessage(header.from, header.to, parsedBody)
	}
}
