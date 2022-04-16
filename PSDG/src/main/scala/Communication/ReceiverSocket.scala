package Communication

import Messaging.Message

import java.io.ObjectInputStream
import java.net.InetSocketAddress
import java.nio.channels.ServerSocketChannel
import scala.collection.mutable

/**
 * The Receiver Socket is opened once and keeps listening for new messages.
 * New messages are then stored in a queue which is checked periodically by the nodes.
 */
class ReceiverSocket(val SocketData: SocketData) extends Thread {

  private val messageQueue = mutable.Queue[Message]()

  /**
   * Get the first message from the queue and remove it from the queue.
   * @return The first message from the queue.
   */
  def getFirstFromQueue(): Message = {
    messageQueue.dequeue()
  }

  /**
   * Check if there a messages in the queue.
   * @return True if the message queue is empty.
   */
  def isQueueEmpty(): Boolean = {
    messageQueue.isEmpty
  }

  /**
   * Open the Receiver Socket and keep it open for connections.
   */
  override def run(): Unit = {
    val socketChannelReceiver = ServerSocketChannel.open
    socketChannelReceiver.configureBlocking(true)

    socketChannelReceiver.socket.bind(new InetSocketAddress(SocketData.address, SocketData.port))

    while (true) {
      val connectionToSocket = socketChannelReceiver.accept
      val ois = new ObjectInputStream(connectionToSocket.socket.getInputStream)
      val messageReceived = ois.readObject.asInstanceOf[Message]
      messageQueue.enqueue(messageReceived)
    }
  }
}