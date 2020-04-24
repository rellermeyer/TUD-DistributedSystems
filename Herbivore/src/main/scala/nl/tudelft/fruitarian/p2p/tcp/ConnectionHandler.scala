package nl.tudelft.fruitarian.p2p.tcp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorRef, Props}
import nl.tudelft.fruitarian.Logger
import nl.tudelft.fruitarian.p2p.messages.FruitarianMessage
import nl.tudelft.fruitarian.p2p.{Address, MessageSerializer, SendMsg}

object ConnectionHandler {
  def props(connection: ActorRef, remote: InetSocketAddress, callback: FruitarianMessage => Unit) =
    Props(classOf[ConnectionHandler], connection, remote, callback);
}

class ConnectionHandler(connection: ActorRef, remote: InetSocketAddress, callback: FruitarianMessage => Unit) extends Actor {
  import akka.io.Tcp._

  // This receive function mostly follows the Client version.
  // TODO: Perhaps generalise Client such that it can be used for both server and client connections.
  def receive: Receive = {
    // Upon getting binary data, send it through the connection.
    case SendMsg(msg: FruitarianMessage) =>
      connection ! Client.messageToWrite(msg)

    case "close" => connection ! Close

    // When the TCP connection is closed, kill this node.
    case PeerClosed =>
      Logger.log("[S] Client connection closed", Logger.Level.INFO)
      context.stop(self)

    // Send any other event to the listener.
    case Received(data) => Client.onMessageReceived(data, callback)
  }
}
