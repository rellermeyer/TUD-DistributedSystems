package Communication

import Messaging.Message

import java.io.ObjectOutputStream
import java.net.InetSocketAddress
import java.nio.channels.SocketChannel
import scala.collection.mutable

/**
 * The Sender Socket is opened every time a new message is send to another node.
 */
@SerialVersionUID(1L)
class SenderSocket() extends Serializable {

  private val messageStoreUnit = mutable.Queue[Message]()

  /**
   * Send a message by opening a Sender Socket and sending the object as object stream.
   */
  def sendMessage(messageToSend: Message, addressReceiver: String, portReceiver: Int): Unit = {

    messageStoreUnit.enqueue(messageToSend)

    val sChannel = SocketChannel.open

    sChannel.configureBlocking(true)

    if (sChannel.connect(new InetSocketAddress(addressReceiver, portReceiver))) {
      val oos = new ObjectOutputStream(sChannel.socket.getOutputStream)
      oos.writeObject(messageToSend)
      oos.close()
    }
  }
}