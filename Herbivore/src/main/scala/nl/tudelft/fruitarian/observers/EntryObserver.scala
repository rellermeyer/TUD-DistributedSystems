package nl.tudelft.fruitarian.observers

import nl.tudelft.fruitarian.models.{DCnet, NetworkInfo, Peer}
import nl.tudelft.fruitarian.p2p.TCPHandler
import nl.tudelft.fruitarian.p2p.messages._
import nl.tudelft.fruitarian.patterns.Observer

import scala.util.Random

class EntryObserver(handler: TCPHandler, networkInfo: NetworkInfo) extends Observer[FruitarianMessage] {
  override def receiveUpdate(event: FruitarianMessage): Unit = event match {
    case EntryRequest(from, to, id) =>
	    // Generate and send common seed to entry node.
      val seed = DCnet.getSeed
	    handler.sendMessage(EntryResponse(to, from, EntryResponseBody(seed.toString, networkInfo.getPeers, networkInfo.nodeId)))
	    networkInfo.cliquePeers += Peer(from, seed, id)
    case EntryResponse(from, to, entryInfo) =>
	    // Generate and send seeds to all peers.
	    entryInfo.peerList.foreach({ case (id, address) =>
		    val seed = DCnet.getSeed
		    networkInfo.cliquePeers += Peer(address, seed, id)
		    handler.sendMessage(AnnounceMessage(to, address, AnnounceMessageBody(seed.toString, networkInfo.nodeId)))
	    })
	    // Add peer with common seed value.
	    networkInfo.cliquePeers += Peer(from, Integer.parseInt(entryInfo.seed), entryInfo.id)
    case AnnounceMessage(from, _, body) =>
	    networkInfo.cliquePeers += Peer(from, Integer.parseInt(body.seed), body.id)

		case _ =>
  }
}
