package nl.tudelft.fruitarian.observers

import nl.tudelft.fruitarian.p2p.TCPHandler
import nl.tudelft.fruitarian.p2p.messages.{FruitarianMessage, TextMessage}
import nl.tudelft.fruitarian.patterns.Observer

/* Example Observer that sends "Hi there!" upon receiving "Hello World" */
class Greeter(handler: TCPHandler) extends Observer[FruitarianMessage] {
  def receiveUpdate(event: FruitarianMessage): Unit = event match {
    case TextMessage(from, to, body) =>
      if (body == "Hello World") {
        handler.sendMessage(TextMessage(to, from, "Hi there!"))
      }
    case _ =>
  }
}
