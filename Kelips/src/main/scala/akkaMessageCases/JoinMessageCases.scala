package akkaMessageCases

import akka.actor.ActorRef
import kelips.SoftState

object JoinMessageCases {
  case class InitJoin(welcomeNode: ActorRef)        // Call this on a node that wants to join, welcomeNode is well-known node
  case class JoinRequest(groupId: Int)
  case class JoinAddress(ip: ActorRef)
  case class RequestJoinView()
  case class SendJoinView(softState: SoftState)
}
