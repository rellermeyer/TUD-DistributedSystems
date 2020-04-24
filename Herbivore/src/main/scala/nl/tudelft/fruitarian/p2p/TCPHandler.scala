package nl.tudelft.fruitarian.p2p

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import nl.tudelft.fruitarian.p2p.messages.FruitarianMessage
import nl.tudelft.fruitarian.patterns.Observer

class TCPHandler(serverPort: Int) {
  // Bind to the machine on the given port.
  val serverHost = new InetSocketAddress("0.0.0.0", serverPort)

  private val serverMessageBus = ServerMessageBus
  private val sys = ActorSystem("TCP")
  private val serverActor = sys.actorOf(tcp.Server.props(
      serverHost, serverMessageBus.onIncomingMessage),
    "TCPServer")

  /* Store a list of active connections to other nodes for further reference */
  // TODO: Filter nodes that have been killed.
  private var connections: List[TCPConnection] = Nil

  def sendMessage(msg: FruitarianMessage): Unit = {
    val connection: TCPConnection = Connections.findConnectionFor(msg.header.to)
      .getOrElse(setupConnectionTo(msg.header.to))
    connection.actor ! SendMsg(msg)
  }

  def addMessageObserver(observer: Observer[FruitarianMessage]): Unit = {
    serverMessageBus.addObserver(observer)
  }

  private def setupConnectionTo(to: Address): TCPConnection = {
    val connection = TCPConnection(
      to,
      sys.actorOf(tcp.Client.props(to.socket, serverMessageBus
        .onIncomingMessage))
    )
    Connections.addConnection(connection)
    connection
  }

  def shutdown(): Unit = {
    sys.terminate()
  }
}
