package nl.tudelft.fruitarian.observers

import nl.tudelft.fruitarian.models.DCnet
import nl.tudelft.fruitarian.observers.helper.ExperimentHelper
import nl.tudelft.fruitarian.p2p.messages.{FruitarianMessage, ResultMessage}
import nl.tudelft.fruitarian.p2p.{MessageSerializer, TCPHandler}
import nl.tudelft.fruitarian.patterns.Observer

class UtilizationObserver(handler: TCPHandler, transmissionObserver: TransmissionObserver) extends
  Observer[FruitarianMessage] {
  var messagesSent = 0
  val noMessages = 600
  var totalBytesReceived = 0
  var messageBytesReceived = 0

  var lastMessage = ""

  def sendNewMessage(): Unit = {
    lastMessage = ExperimentHelper.generateRandomMessage(DCnet.MESSAGE_SIZE)
    transmissionObserver.queueMessage(lastMessage)
    messagesSent += 1
  }

  /* Start experiment */
  sendNewMessage()

  override def receiveUpdate(event: FruitarianMessage): Unit = event match {
    case ResultMessage(from, to, message) if message == lastMessage =>
      if (messagesSent <= noMessages) {
        totalBytesReceived += MessageSerializer.serializeMsg(event).getBytes().length
        messageBytesReceived += message.getBytes().length
        println("[UTILIZATION] messages sent: " + messagesSent
          + " total in Kb: " + (math rint totalBytesReceived * 8) / 1024
          + " correct messages in (Kb): " + (math rint messageBytesReceived * 8) / 1024
          + " effective bandwidth utilization: " + messageBytesReceived.toFloat / totalBytesReceived)
        sendNewMessage()
      }
    case _ =>
      totalBytesReceived += MessageSerializer.serializeMsg(event).getBytes().length
  }
}
