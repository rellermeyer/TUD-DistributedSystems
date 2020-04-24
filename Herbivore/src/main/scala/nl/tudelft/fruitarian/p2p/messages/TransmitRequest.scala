package nl.tudelft.fruitarian.p2p.messages

import nl.tudelft.fruitarian.p2p.{Address, MessageSerializer}
import org.json4s.Formats
import org.json4s.jackson.Serialization.{read, write}

case class TransmitRequest(from: Address, to: Address, roundId: Int) extends FruitarianMessage(MessageHeader(TransmitRequest.MessageType, from, to)) {
	override def serializeBody(): String = {
		write(roundId.toString)
	}
}

case object TransmitRequest {
	val MessageType = "TRANSMIT_REQUEST"
	implicit val formats: Formats = MessageSerializer.messageSerializeFormats

	def fromHeaderAndBody(header: MessageHeader, body: String): FruitarianMessage = {
		TransmitRequest(header.from, header.to, read[String](body).toInt)
	}
}
