package chatapp.model

import chatapp.ChatUser
import io.circe.syntax._
import io.circe.{Encoder, Json, _}

@SerialVersionUID(235465L)
class ChatMessage(
	val body: String,
    val author: ChatUser,
    val timestamp: Long = java.time.Instant.now.getEpochSecond())
extends Serializable {

	//	def encoded: Json = Encoder.forProduct2("author", "body")(m => m.body, m.author.username)

	override def toString: String = {
		s"${author.username}: $body"
	}

	override def equals(obj: Any): Boolean = {
		obj match {
			case other: ChatMessage => this.body == other.body && this.author == other.author && this.timestamp == other.timestamp
			case _ => false
		}
	}

}

object ChatMessage {
	implicit val encodeChatMessage: Encoder[ChatMessage] = new Encoder[ChatMessage] {
		final def apply(m: ChatMessage): Json = Json.obj(
			("author", m.author.asJson),
//			("author", Json.fromString(m.author.username)),
			("body", Json.fromString(m.body)),
			("timestamp", Json.fromLong(m.timestamp))
		)
	}

	implicit val decodeAtomicObjectState: Decoder[ChatMessage] = new Decoder[ChatMessage] {
		final def apply(c: HCursor): Decoder.Result[ChatMessage] =
			for {
				author <- c.downField("author").as[ChatUser]
				body <- c.downField("body").as[String]
				timestamp  <- c.downField("timestamp").as[Long]
			} yield {
				new ChatMessage(body, author)
			}
	}

}
