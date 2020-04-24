package akkaMessageCases

import kelips.{ContactNode, GroupEntry}

object UtilMessageCases {
  case class AddContactNode(contactNode: ContactNode)           // Call this on a node to add contact to that node
  case class AddGroupEntry(groupEntry: GroupEntry) // Call this on a node to add a group entry
}
