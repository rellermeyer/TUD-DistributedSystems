package kelips

import akka.actor.ActorRef
import akkaMessageCases.FileMessageCases
import kelips.Utils.parseActor
import main.scala.kelips.AVLTree

import scala.collection.mutable.ListBuffer

/**
  * This class represents the softstate of a node. It contains all data this node knows about.
  * @param affinityGroupView The view of the owning node's affinity group view
  * @param contacts The list of contacts to all other groups
  * @param fileTuples The list of fileTuples
  */
class SoftState(val affinityGroupView: AffinityGroupView,
                val contacts: ListBuffer[Contact],
                val fileTuples: AVLTree[FileTuple]) {

  /*
  The logBook contains the information gossiped to this node's affinityGroupView at every heartbeat.
   */
  var logBook: LogBook = new LogBook(new ListBuffer[GossipItem])
  val heartBeatLimit = 300    // The limit of heartbeat difference between the entry and the node's own. Delete when exceeded
  var heartBeat: Int = 0    // Init the hearbeat
  val contactSize = 2       // The maximum amount of contactNodes in another group

  /**
   * Looks up a file. This is the "querying node".
   * Firstly, the name of the file is hashed to find
   * the affinity group it is stored in. Then, a contact
   * in that group is retrieved from the contracts.
   * Lastly, the file is actually retrieved from the
   * node.
   *
   * @param fileName the name of the file to be looked up.
   * @param numberOfGroups number of affinity groups in
   *                       the system.
   */
  def lookupFile(fileName: String, numberOfGroups: Int, actor: ActorRef) {
    val fileAffinityGroup: Int = Math.floorMod(Utils.hash(fileName), numberOfGroups)
    var contact: ActorRef = null

    contacts.toList.foreach(c => {
      // Simply get the first node from the contacts in
      // that affinity group to send file lookup request to.
      if (c.groupId == fileAffinityGroup) {
        contact = c.contactNodes.head.actorRef
      }
    })

    // If no contacts was found, then the file is in our
    // own affinity group and should be looked for here.
    if (contact == null) {
      affinityGroupView.groupEntries.head.actorRef ! FileMessageCases.RequestFile(actor, fileName)
    } else {
      // Else, contact a node from other affinity group
      // to serve the file.

      contact ! FileMessageCases.RequestFile(actor, fileName)
    }
  }

  /**
   * Inserts a file. This is the "origin node". Firstly,
   * the name of the file is hashed to find the affinity
   * group it should be stored in. Then, a contact in
   * that group is selected to handle the file insertion.
   * Lastly, the node forwards the insertion request to
   * the selected node.
   */
  def insertFile(fileName: String, numberOfGroups: Int, replicationFactor: Int, actor: ActorRef) {
    val fileAffinityGroup: Int = Math.floorMod(Utils.hash(fileName), numberOfGroups)
    var contact: ActorRef = null
    contacts.foreach(c => {
      // Simply get the first node from the contacts in
      // that affinity group to send file lookup request to.
      if (c.groupId == fileAffinityGroup) contact = c.contactNodes.head.actorRef
    })

    // If no contacts was found, then the file is in our
    // own affinity group and should be looked for here.
    if (contact == null) {
      affinityGroupView.groupEntries.head.actorRef ! FileMessageCases.InsertRequest(actor, fileName, replicationFactor)
    } else {
      // Else, contact a node from other affinity group
      // to serve the file.

      contact ! FileMessageCases.InsertRequest(actor, fileName, replicationFactor)
    }

  }

  /**
    * Update the heartbeat of a fileTuple.
    * @param fileTuple The FileTuple to update
    */
  def updateFileTuple(fileTuple: FileTuple): Unit = {
    fileTuples.foreach(item => {
      if (item.isSameData(fileTuple) && item.heartBeatID < fileTuple.heartBeatID) {
        item.heartBeatCounter = this.heartBeat
        item.heartBeatID = fileTuple.heartBeatID
        logBook.updateLog(item)
      }
    })
  }

  /**
    * Return true if the fileTuple is present in the softstate, false otherwise
    * @param tuple The tuple to check for
    * @return
    */
  def hasFile(tuple: FileTuple): Boolean = {
    fileTuples.foreach(item => {
      if (item.isSameData(tuple)) return true
    })
    false
  }

  /**
    * Return true if this ContactNode is present in the softstate
    * @param data The ContactNode to test for
    * @return
    */
  def hasContactNode(data: ContactNode): Boolean = {
    contacts.foreach(contact => {
      if (contact.contains(data)) return true
    })
    false
  }

  /**
    * Update the heartbeat of this ContactNode.
    * @param contactNode The ContactNode to update for
    */
  def updateContactNode(contactNode: ContactNode): Unit = {
    contacts.foreach(item => {
      if (item.contains(contactNode)) {
        item.contactNodes.foreach(ourContactNode => {
          if (ourContactNode.isSameData(contactNode) && ourContactNode.heartBeatID < contactNode.heartBeatID) {
            ourContactNode.heartBeatCounter = heartBeat
            ourContactNode.heartBeatID = contactNode.heartBeatID
            logBook.updateLog(ourContactNode)
          }
        })
      }
    })
  }

  /**
    * Add this ContactNode to the correct Contact holder
    * @param contactNode The ContactNode to add
    */
  def addContactNode(contactNode: ContactNode): Unit = {
    contacts.foreach(contact => {
      if (contact.groupId == contactNode.groupID) {
        contact.contactNodes += contactNode
      }
    })
  }


  /**
    * Add Data to softState.
    * This method checks whether the data is already present in the softstate, and whether there is space.
    * @param data The Data to be added
    */
  def addData(data: Data) = {
    data match {
      case data: GroupEntry =>
        if (!affinityGroupView.hasEntry(data)) {
          val newGroupEntry = data.clone()
          newGroupEntry.heartBeatCounter = heartBeat
          affinityGroupView.addGroupEntry(newGroupEntry)
          logBook.addLog(newGroupEntry)
        }
      case data: ContactNode =>
        if (!hasContactNode(data) && contactHasFreeNodeSpace(data)) {
          val newContactNode = data.clone()
          newContactNode.heartBeatCounter = heartBeat
          addContactNode(newContactNode)
          logBook.addLog(newContactNode)
        }
      case data: FileTuple =>
        if (!hasFile(data)) {
          val newFileTuple = data.clone()
          newFileTuple.heartBeatCounter = heartBeat
          fileTuples += newFileTuple
          logBook.addLog(data)
        }
    }
  }

  /**
    * Remove data, if present, to softstate. Use with caution.
    * @param data The data to remove
    */
  def removeData(data: Data): Unit = data match {
    case data: GroupEntry => {
      if (affinityGroupView.hasEntry(data)) {
        affinityGroupView.deleteGroupEntry(data)
        logBook.removeLog(data)
      }
    }
    case data: ContactNode => {
      if (hasContactNode(data)) {
        contacts.find(contact => contact.groupId == data.groupID).get.contactNodes -= data
        logBook.removeLog(data)
      }
    }
    case data: FileTuple => {
      if (hasFile(data)) {
        fileTuples -= data
        logBook.removeLog(data)
      }
    }
  }

  /**
    * Update the heartbeat of this groupEntry if this update is not seen before. Also then add to our own gossip stream.
    * @param groupEntry The GroupEntry to update the heartbeat of
    */
  def updateGroupEntry(groupEntry: GroupEntry): Unit = {
    affinityGroupView.groupEntries.foreach(item => {
      if (item.actorRef.equals(groupEntry.actorRef) && item.heartBeatID < groupEntry.heartBeatID) {
        item.heartBeatCounter = heartBeat
        item.heartBeatID = groupEntry.heartBeatID
        logBook.updateLog(item)
      }
    })
  }

  /**
    * Check whether the Contact holder has enough space for more ContactNodes.
    * @param contactNode The ContactNode to add
    */
  def contactHasFreeNodeSpace(contactNode: ContactNode): Boolean = {
    contacts.foreach(contact => {
      if (contact.groupId == contactNode.groupID && contact.contactNodes.size < contactSize) return true
    })
    false
  }

  /**
    * Update data, if present, else add data
    * @param data The Data to update the heartbeat off
    */
  def updateData(data: Data): Unit = data match {
    case data: GroupEntry => {
      if (!affinityGroupView.hasEntry(data)) {
        val newGroupEntry = data.clone()
        newGroupEntry.heartBeatCounter = heartBeat
        affinityGroupView.addGroupEntry(newGroupEntry)
        logBook.addLog(newGroupEntry)
      }
      else {
        updateGroupEntry(data)
      }
    }
    case data: ContactNode => {
      if (!hasContactNode(data) && contactHasFreeNodeSpace(data)) {
        val newContactNode = data.clone()
        newContactNode.heartBeatCounter = heartBeat
        addContactNode(newContactNode)
        logBook.addLog(newContactNode)
      }
      else {
        updateContactNode(data)
      }
    }
    case data: FileTuple => {
      if (!hasFile(data)) {
        val newFileTuple = data.clone()
        newFileTuple.heartBeatCounter = heartBeat
        fileTuples += newFileTuple
        logBook.addLog(data)
      }
      else {
        updateFileTuple(data)
      }
    }
  }

  /**
    * Update the softstate with the logBook received from a gossip within its own AffinityGroup.
    * @param logBook The LogBook to update with
    */
  def update(logBook: LogBook): Unit = {
    logBook.gossipItemList.foreach(gossipItem => {
      gossipItem.eventType match {
        case 'a' => addData(gossipItem.data)
        case 'r' => removeData(gossipItem.data)
        case 'u' => updateData(gossipItem.data)
      }
    })
  }

  /**
    * Get a random GroupEntry from groupEntriesList
    * @param groupEntriesList The list to pick the GroupEntry from
    * @return A random GroupEntry from the list
    */
  def getRandomGroupEntry(groupEntriesList: ListBuffer[GroupEntry]): (ActorRef, Int) = {
    val groupEntry = groupEntriesList((Math.random() * groupEntriesList.size).floor.toInt)
    (groupEntry.actorRef, groupEntry.heartBeatID)
  }

  /**
    * Update the softstate with data received from a node in another group.
    * @param affinityGroupId The id of the group of the sending node
    * @param groupEntriesList The GroupEntries this sending node has
    * @param theirContacts The Contacts this sending node has
    */
  def updateFromContact(affinityGroupId: Int, groupEntriesList: ListBuffer[GroupEntry], theirContacts: ListBuffer[Contact]): Unit = {
    contacts.foreach(ourContact => {
      if (ourContact.groupId == affinityGroupId) {
        // Add a random GroupEntry of this node's groupEntriesList if there is space and it is not already present
        if (ourContact.contactNodes.size < contactSize) {
          val actor = getRandomGroupEntry(groupEntriesList)
          val newContactNode = new ContactNode(actor._1, affinityGroupId, heartBeat, actor._2)
          if (!ourContact.contains(newContactNode)) {
            addContactNode(newContactNode)
            logBook.addLog(newContactNode)
          }
        }
        // Update all ContactNodes in the Contact holder for the group of sending node
        ourContact.contactNodes.foreach(ours => {
          groupEntriesList.foreach(theirs => {
            if (ours.actorRef.equals(theirs.actorRef) && ours.heartBeatID < theirs.heartBeatID) {
              ours.heartBeatCounter = heartBeat
              ours.heartBeatID = theirs.heartBeatID
              logBook.updateLog(ours)
            }
          })
        })
      }
      // Go through received contactList to update/add to contacts
      theirContacts.foreach(theirContact => {
        if (ourContact.groupId == theirContact.groupId) {
          theirContact.contactNodes.foreach(theirContactNode => {
            if (!ourContact.contains(theirContactNode) && ourContact.contactNodes.size < contactSize) {
              val newContactNode = new ContactNode(theirContactNode.actorRef, theirContactNode.groupID, heartBeat, theirContactNode.heartBeatID)
              addContactNode(newContactNode)
              logBook.addLog(newContactNode)
            } else if (ourContact.contains(theirContactNode)) {
              ourContact.contactNodes.foreach(ourContactNode => {
                if (ourContactNode.isSameData(theirContactNode) && ourContactNode.heartBeatID < theirContactNode.heartBeatID) {
                  ourContactNode.heartBeatID = theirContactNode.heartBeatID
                  ourContactNode.heartBeatCounter = theirContactNode.heartBeatCounter
                  logBook.updateLog(ourContactNode)
                }
              })
            }
          })
        }
      })
    })
  }

  /**
    * Check the softstate for expired Data and remove this data.
    * @param heartBeat The current heartbeat
    * @param joinIp The softstate's owning node/actor, only used for printing
    */
  def checkForExpiry(heartBeat: Int, joinIp: ActorRef): Unit = {
    this.heartBeat = heartBeat
    logBook.removePotentialItems(heartBeat)
    affinityGroupView.groupEntries.foreach(groupEntry => {
      if (groupEntry.heartBeatCounter < heartBeat - heartBeatLimit) {
        affinityGroupView.groupEntries -= groupEntry
        println(parseActor(joinIp.toString()) + " removed group entry " + parseActor(groupEntry.actorRef.toString()) + " from softstate with " + groupEntry.heartBeatID + " as last heard of heartbeat")
      }
    })
    contacts.foreach(contact => {
      contact.contactNodes.foreach(contactNode => {
        if (contactNode.heartBeatCounter < heartBeat - heartBeatLimit) {
          contact.contactNodes -= contactNode
          println(parseActor(joinIp.toString()) + " Removed contactNode " + parseActor(contactNode.actorRef.toString()) + " from softstate with " + contactNode.heartBeatID + " as last heard of heartbeat")
        }
      })
    })
    fileTuples.foreach(fileTuple => {
      if (fileTuple.heartBeatCounter < heartBeat - heartBeatLimit) {
        fileTuples -= fileTuple
        println(parseActor(joinIp.toString()) + " Removed filetuple " + fileTuple.fileName + " from " + parseActor(affinityGroupView.groupEntries.head.actorRef.toString()) + "'s group, with " + fileTuple.heartBeatID + " as last heard of heartbeat")
      }
    })
  }
}
