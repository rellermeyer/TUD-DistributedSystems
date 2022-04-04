package services

import akka.actor.ActorSelection
import akka.actor.typed.scaladsl.ActorContext
import peer.PeerMessage

/**
 * Get a reference to a peer from its path.
 */
object GetPeerRef {
  def apply(context: ActorContext[PeerMessage], path: String): ActorSelection = {
    context.system.classicSystem.actorSelection(path)
  }
}
