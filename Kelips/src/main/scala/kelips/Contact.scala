package kelips

import scala.collection.mutable.ListBuffer


class Contact(val groupId: Int, var contactNodes: ListBuffer[ContactNode]) {

  def contains(otherContactNode: ContactNode): Boolean = {
    contactNodes.foreach(contactNode => {
      if (contactNode.isSameData(otherContactNode)) return true
    })
    false
  }

  override def clone(): Contact = {
    new Contact(groupId, contactNodes.clone())
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case otherContact: Contact => {
        if (groupId.equals(otherContact.groupId)
          && contactNodes.equals(otherContact.contactNodes))
          true
        else false
      }
      case _ => false
    }
  }
}
