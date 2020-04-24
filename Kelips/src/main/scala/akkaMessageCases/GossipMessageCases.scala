package akkaMessageCases

import kelips.{Contact, GroupEntry, LogBook}

import scala.collection.mutable.ListBuffer

object GossipMessageCases {
  case class Gossip(logBook: LogBook)
  case class StartGossip()
  case class GossipGroupEntriesAndContacts(groupId: Int, affinityGroupEntries: ListBuffer[GroupEntry], contacts: ListBuffer[Contact])

}
