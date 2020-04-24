package nl.tudelft.fruitarian.p2p.tcp

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import nl.tudelft.fruitarian.Logger
import nl.tudelft.fruitarian.p2p.messages.FruitarianMessage
import nl.tudelft.fruitarian.p2p.{Address, Connections, TCPConnection}

object Server {
  def props(host: InetSocketAddress, callback: FruitarianMessage => Unit) =
    Props(classOf[Server], host, callback);
}

/**
 * Listens to TCP connections from the outside.
 * Upon another client connecting to the server a tcp.ConnectionHandler actor
 * is set up to deal with the connection.
 */
class Server(host: InetSocketAddress, callback: FruitarianMessage => Unit) extends
  Actor {
  implicit val system: ActorSystem = context.system

  // Bind the server to the given host.
  IO(Tcp) ! Bind(self, host)

  /**
   * Upon receiving a TCP message.
   */
  def receive: Receive = {
    // When the bound to our IO(Tcp) listener is completed.
    case b @ Bound(localAddress) =>
      Logger.log(s"[S] Listening on [$localAddress]", Logger.Level.INFO)

    // When the bound to our IO(Tcp) listener failed.
    case CommandFailed(_: Bind) => context.stop(self)

    // Upon connection to the socket, set up an actor to handle that specific
    // connection.
    case c @ Connected(remote, local) =>
      val connection = sender()
      val handler = context.actorOf(ConnectionHandler.props(connection, remote, callback))
      connection ! Register(handler)
      Logger.log(s"[S] Client connected from [$remote]", Logger.Level.INFO)
      Connections.addConnection(TCPConnection(Address(remote), handler))
  }
}
