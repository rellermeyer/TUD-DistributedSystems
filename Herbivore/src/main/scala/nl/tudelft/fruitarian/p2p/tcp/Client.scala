package nl.tudelft.fruitarian.p2p.tcp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import nl.tudelft.fruitarian.Logger
import nl.tudelft.fruitarian.p2p.messages.FruitarianMessage
import nl.tudelft.fruitarian.p2p.{Address, MessageSerializer, SendMsg}

object Client {
  def messageToWrite(msg: FruitarianMessage) = Write(ByteString.fromString(MessageSerializer.serializeMsg(msg)))
  def onMessageReceived(data: ByteString, callback: FruitarianMessage => Unit) = {
    try {
      val msg = MessageSerializer.deserialize(data.decodeString("utf-8"))
      callback(msg)
    } catch {
      case e: Error => Logger.log(e.toString, Logger.Level.ERROR)
    }
  }

  def props(remote: InetSocketAddress, callback: FruitarianMessage => Unit) =
    Props(classOf[Client], remote, callback);
}

/**
 * The TCP client sets up a TCP connection to a given remote. Keeping a
 * listener actor up to date on what happens on that TCP connection.
 * @param remote The remote address to connect to.
 */
class Client(remote: InetSocketAddress, callback: FruitarianMessage => Unit) extends
  Actor {
  implicit val system: ActorSystem = context.system
  var queue: List[FruitarianMessage] = Nil

  // Connect to the desired tcp remote.
  IO(Tcp) ! Connect(remote)

  def receive = {
    // Upon connection failure, let listener know and kill.
    case CommandFailed(_: Connect) =>
      println(s"[C] Failed establishing new connection to [$remote]")
      context.stop(self)

    // When a message is sent but the connection is not yet ready, enqueue it.
    case SendMsg(msg: FruitarianMessage) => queue = msg :: queue

    // Upon connection success.
    case c @ Connected(remote, local) =>
      val connection = sender()
      connection ! Register(self)

      // Clear message queue when connection was established.
      queue.foreach((msg: FruitarianMessage) => connection ! Client.messageToWrite(msg))
      queue = Nil

      Logger.log(s"[C] Connection established to [$remote]", Logger.Level.INFO)

      context.become {
        // Upon getting binary data, send it through the connection.
        case SendMsg(msg: FruitarianMessage) =>
          connection ! Client.messageToWrite(msg)

        // If the write failed due to OS buffer being full.
        case CommandFailed(w: Write) => Logger.log("[C] Write Failed", Logger.Level.ERROR)

        // When data received, send it to the listener.
        case Received(data: ByteString) => Client.onMessageReceived(data, callback)

        // On close command.
        case "close" => connection ! Close

        // Upon receiving the close message.
        case _: ConnectionClosed =>
          Logger.log(s"[C] Connection Closed with [$remote]", Logger.Level.INFO)
          context.stop(self)
      }
  }
}
