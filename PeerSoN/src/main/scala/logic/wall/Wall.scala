package logic.wall

import akka.actor.typed.scaladsl.ActorContext
import dht.DHT
import peer.{Message, PeerMessage}
import services.Encrypt

import scala.collection.mutable

/**
 * Async Wall Protocol:
 * wi - The wall index file contains a list of all wall entries a user has.
 * we - This file contains one wall entry. The number of the wall entry is successive
 * and defined on the receiver's side
 * ----------------------------------------------------------------------------------------
 * To put a message on another user's Wall, the following key-value pair is stored in DHT
 * (key, value)
 * key - ${hashedMail}@we${index}, where ${hashedMail} is the hashed mail of the receiver
 * value - WallEntry(sender, content)
 * ----------------------------------------------------------------------------------------
 * The Wall object manages methods to update the Wall entries to DHT
 */

object Wall {
  /**
   *
   * @param index   the current index of the entry
   * @param sender  the sender email (not hashed)
   * @param content the file/message content
   */
  case class WallEntry(index: Int, sender: String, content: String) extends File

  /**
   * The WallIndex File, stored in the DHT in the key-value form of
   * wallIndexKey -> WallIndex(owner, lastEntryIndex, entries)
   *
   * @param hashedMail     who owns the wall, email (not hashed)
   * @param lastEntryIndex the index of the most recent entry (i.e., the current last index)
   * @param entries        a list buffer of all wall entries
   */
  case class WallIndex(hashedMail: String, lastEntryIndex: Int, entries: mutable.ListBuffer[String])


  /**
   * initialization:
   * create a WallIndex entry for owner if not existed
   *
   * @param owner who owns the wall, mail (not hashed)
   */
  def load(context: ActorContext[PeerMessage], owner: String, dht: DHT): Unit = {
    val wallIndexKey: String = getWallIndexKey(owner)
    // if wallIndexKey found, then try to fetch the wall entries
    dht.contains(wallIndexKey, { res =>
      if (res) {
        val wallIndexLookup = dht.get(wallIndexKey, {
          case Some(ownerWallIndex: WallIndex) =>
            val wallEntryKeyBuffer = ownerWallIndex.entries
            wallEntryKeyBuffer.foreach(wallEntryKey => {
              val lookup = dht.get(wallEntryKey, {
                case Some(currentWallEntry: WallEntry) =>
                  context.self ! Message(currentWallEntry.sender, currentWallEntry.content, ack = false)
                  // remove from DHT
                  dht.remove(wallEntryKey)
                case _ => println(s"WallEntry under ${wallEntryKey} not found")
              })

            })
          case _ => println(s"WallIndex under ${wallIndexKey} not found")
        })
      }
    })

    dht.put(wallIndexKey, WallIndex(Encrypt(owner), -1, mutable.ListBuffer.empty))
  }

  def getWallIndexKey(owner: String): String = {
    s"${owner}@wi"
  }

  /**
   * generate a wall entry key
   *
   * @param owner mail (not hashed)
   * @param index current index for the wall entry
   * @return wall entry key
   */
  def getWallEntryKey(owner: String, index: Int): String = {
    s"${owner}@we${index}"
  }
}
