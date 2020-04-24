package kelips

import akka.actor.ActorRef

import scala.collection.mutable.ListBuffer

class AffinityGroupView {

  var groupEntries: collection.mutable.Buffer[GroupEntry] = ListBuffer()

  /**
    * Add a group entry
    * @param groupEntry group entry to add
    * @return true if successfully added, false otherwise
    */
  def addGroupEntry(groupEntry: GroupEntry): Boolean = {
    if (groupEntry == null) {
      return false
    }
    groupEntries += groupEntry
    true
  }

  /**
    * Delete a group entry
    * @param groupEntry group entry to delete
    * @return true if successfully deleted, false otherwise
    */
  def deleteGroupEntry(groupEntry: GroupEntry): Boolean = {
    if (!groupEntries.contains(groupEntry)) {
      return false
    }
    groupEntries -= groupEntry
    true
  }

  /**
    * Check if this group entry is present
    * @param data
    * @return
    */
  def hasEntry(data: GroupEntry): Boolean = {
    groupEntries.foreach(groupEntry => {
      if (groupEntry.actorRef.equals(data.actorRef)) return true
    })
    false
  }


  /**
   * Get a GroupEntry uniformly at random.
   * @return a random kelips.GroupEntry.
   */
  def getRandomGroupEntry(selfActor: ActorRef): ActorRef = {
    val groupSize = groupEntries.length + 1
    println("Groupsize: " + groupSize)
    val random = scala.util.Random.nextInt(groupSize)
    if (random == groupSize - 1) {
      selfActor
    } else {
      groupEntries(random).actorRef
    }
  }
}
