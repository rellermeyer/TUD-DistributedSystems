package nl.tudelft.fruitarian.models

import java.net.{InetAddress, InetSocketAddress}

import nl.tudelft.fruitarian.p2p.Address

import scala.collection.mutable.ArrayBuffer

class NetworkInfo(serverPort: Int) {
  val cliquePeers: ArrayBuffer[Peer] = ArrayBuffer[Peer]()
  var ownAddress: Address = Address(new InetSocketAddress(InetAddress.getLocalHost.getHostAddress, serverPort))
  val nodeId: String = java.util.UUID.randomUUID.toString
  var chatMode: Boolean = false

  // Returns the addresses of all peers.
  def getPeers: List[(String, Address)] = {
    cliquePeers.map(p => (p.id, p.address)).toList
  }

  def getNextPeer: Option[Peer] = {
    val sorted = cliquePeers.toList.sortBy(_.id)
    val bigger = sorted.filter(p => p.id > nodeId)
    if (bigger.nonEmpty) {
      Some(bigger.head)
    } else sorted.headOption
  }

}
