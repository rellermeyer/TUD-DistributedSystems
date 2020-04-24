package nl.tudelft.fruitarian.p2p

import nl.tudelft.fruitarian.p2p.messages.FruitarianMessage
import nl.tudelft.fruitarian.patterns.Subject

object ServerMessageBus extends Subject[FruitarianMessage] {
  def onIncomingMessage(message: FruitarianMessage): Unit = {
    super.notifyObservers(message)
  }
}
