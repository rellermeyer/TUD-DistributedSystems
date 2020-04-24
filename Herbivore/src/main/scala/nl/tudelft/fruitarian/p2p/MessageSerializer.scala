package nl.tudelft.fruitarian.p2p

import java.net.InetSocketAddress

import com.fasterxml.jackson.core.JsonParseException
import nl.tudelft.fruitarian.Logger
import nl.tudelft.fruitarian.p2p.messages._
import org.json4s._
import org.json4s.jackson.JsonMethods._

// Simple case class serialisation with help of:
// https://commitlogs.com/2017/01/14/serialize-deserialize-json-with-json4s-in-scala/

/** Simple Helper to serialize and deserialize messages. */
object MessageSerializer {
  val messageSerializeFormats: Formats = DefaultFormats + InetSocketAddressSerializer
  implicit val formats: Formats = messageSerializeFormats

  def serializeMsg(msg: FruitarianMessage): String = {
    compact(render(JObject(("header", Extraction.decompose(msg.header)), ("body", JString(msg.serializeBody())))))
  }
  def deserialize(data: String): FruitarianMessage = {
    try {
      parse(data) match {
        case JObject(("header", h) :: ("body", JString(body)) :: Nil) => h.extract[MessageHeader] match {
          case header@MessageHeader(TextMessage.MessageType, _, _) => TextMessage.fromHeaderAndBody(header, body)
          case header@MessageHeader(EntryResponse.MessageType, _, _) => EntryResponse.fromHeaderAndBody(header, body)
          case header@MessageHeader(EntryRequest.MessageType, _, _) => EntryRequest.fromHeaderAndBody(header, body)
          case header@MessageHeader(TransmitRequest.MessageType, _, _) => TransmitRequest.fromHeaderAndBody(header, body)
          case header@MessageHeader(AnnounceMessage.MessageType, _, _) => AnnounceMessage.fromHeaderAndBody(header, body)
          case header@MessageHeader(TransmitMessage.MessageType, _, _) => TransmitMessage.fromHeaderAndBody(header, body)
          case header@MessageHeader(ResultMessage.MessageType, _, _) => ResultMessage.fromHeaderAndBody(header, body)
          case header@MessageHeader(NextRoundMessage.MessageType, _, _) => NextRoundMessage.fromHeaderAndBody(header, body)
          case MessageHeader(msgType, _, _) => throw new Error(s"Unsupported message type: $msgType received.")
        }
        case _ => throw new Error("Message could not be deserialized.")
      }
    } catch {
      case e: JsonParseException => throw new Error(s"Unable to parse message json: ${e.toString}")
    }
  }
}

case object InetSocketAddressSerializer extends
  CustomSerializer[InetSocketAddress](format => ({
    case JObject(List(
      JField("addr", JString(addr)),
      JField("port", JString(port))
    )) => new InetSocketAddress(addr, port.toInt)
  }, {
    case socket: InetSocketAddress => JObject(List(
      JField("addr", JString(socket.getHostString)),
      JField("port", JString(socket.getPort.toString))
    ))
  }))
