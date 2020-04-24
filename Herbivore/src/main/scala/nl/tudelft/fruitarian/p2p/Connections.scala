package nl.tudelft.fruitarian.p2p

import java.net.InetSocketAddress

import akka.actor.ActorRef
import nl.tudelft.fruitarian.p2p.messages.FruitarianMessage

/** Singleton for connections management. */
object Connections {
  private var connections: List[TCPConnection] = Nil

  def addConnection(connection: TCPConnection): Unit =
    connections = connection :: connections

  def findConnectionFor(address: Address): Option[TCPConnection] =
    connections.find(_.address == address)

  def closeConnections(): Unit = connections.foreach(_.actor ! "close")
}
case class Address(socket: InetSocketAddress)
case class TCPConnection(address: Address, actor: ActorRef)
/** Case class used to define an actor command to given send message. */
final case class SendMsg(msg: FruitarianMessage)