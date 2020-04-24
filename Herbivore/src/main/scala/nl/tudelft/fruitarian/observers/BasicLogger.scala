package nl.tudelft.fruitarian.observers

import nl.tudelft.fruitarian.Logger
import nl.tudelft.fruitarian.p2p.messages.{FruitarianMessage, NextRoundMessage, ResultMessage, TextMessage, TransmitMessage, TransmitRequest}
import nl.tudelft.fruitarian.patterns.Observer

/* Example Observer that logs all incoming messages. */
object BasicLogger extends Observer[FruitarianMessage] {

  def stripNonReadableBytes(message: String): String = {
    message.replaceAll("[^ -~]", "")
  }

  def receiveUpdate(event: FruitarianMessage): Unit = event match {
    case TextMessage(from, _, message) => stripNonReadableBytes(message) match {
      case s if s.nonEmpty => Logger.log(s"[${from.socket}][TEXT]: $message", Logger.Level.INFO)
      case _ =>
    }
    case ResultMessage(from, _, message) => stripNonReadableBytes(message) match {
      case s if s.nonEmpty => Logger.log(s"[${from.socket}][RESULT]: $message", Logger.Level.INFO)
      case _ =>
    }
    case TransmitRequest(from, _, roundId) => Logger.log(s"[${from.socket}][R$roundId][MESSAGE_REQUEST]", Logger.Level.INFO)
    case TransmitMessage(from, _, (roundId, message)) => Logger.log(s"[${from.socket}][R$roundId][BYTE][${message.length}]: 0x${message.map("%02X" format _).mkString.take(20)}...[truncated]", Logger.Level.INFO)
    case NextRoundMessage(from, _, roundId) => Logger.log(s"[${from.socket}] NEXT ROUND R${roundId}", Logger.Level.INFO)
    case _ => Logger.log(event.toString, Logger.Level.INFO)
  }
}
