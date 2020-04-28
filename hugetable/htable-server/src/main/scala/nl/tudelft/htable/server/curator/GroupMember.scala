package nl.tudelft.htable.server.curator

import java.io.Closeable

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.{ChildData, PathChildrenCache, PathChildrenCacheEvent}
import org.apache.curator.framework.recipes.nodes.PersistentNode
import org.apache.curator.utils.ZKPaths
import org.apache.zookeeper.CreateMode

import scala.collection.mutable

/**
 * Group membership management. Adds this instance into a group and keeps a cache of members in the group.
 *
 * @param client The [CuratorFramework] ZooKeeper client to use.
 * @param path The directory in which the members will be located.
 * @param id The unique identifier of this group member.
 * @param payload The payload of the member.
 */
private[htable] class GroupMember(client: CuratorFramework, path: String, val id: String, payload: Array[Byte])
    extends Closeable {

  private val node = new PersistentNode(client, CreateMode.EPHEMERAL, false, ZKPaths.makePath(path, id), payload)
  private val cache = new PathChildrenCache(client, path, true)

  /**
   * Start the group membership.
   */
  def start(): Unit = {
    node.start()
    cache.start()
  }

  /**
   * Add a listener to the membership state.
   */
  def addListener(listener: GroupMemberListener): Unit = {
    cache.getListenable.addListener((_: CuratorFramework, event: PathChildrenCacheEvent) => {
      event.getType match {
        case PathChildrenCacheEvent.Type.CHILD_ADDED =>
          listener.memberJoined(event.getData)
        case PathChildrenCacheEvent.Type.CHILD_UPDATED =>
          listener.memberUpdated(event.getData)
        case PathChildrenCacheEvent.Type.CHILD_REMOVED =>
          listener.memberLeft(event.getData)
        case _ => // We do not handle other events for now
      }
    })
  }

  /**
   * Return the current view of membership. The keys are the IDs
   * of the members. The values are each member's payload
   *
   * @return membership
   */
  def getMembers: Map[String, Array[Byte]] = {
    val res = mutable.HashMap[String, Array[Byte]]()
    var thisIdAdded = false

    cache.getCurrentData.forEach { data =>
      val id = ZKPaths.getNodeFromPath(data.getPath);
      thisIdAdded ||= id == this.id
      res.put(id, data.getData)
    }

    if (!thisIdAdded) {
      res.put(this.id, node.getData) // this instance is always a member
    }
    res.toMap
  }

  /**
   * Stop the group membership.
   */
  override def close(): Unit = {
    import org.apache.curator.utils.CloseableUtils
    CloseableUtils.closeQuietly(cache)
    CloseableUtils.closeQuietly(node)
  }
}

/**
 * A listener for group events.
 */
trait GroupMemberListener {

  /**
   * This method is invoked when a new member joined the group.
   *
   * @param data The data of the member.
   */
  def memberJoined(data: ChildData): Unit = {}

  /**
   * This method is invoked when a member's data is updated.
   *
   * @param data The data of the member.
   */
  def memberUpdated(data: ChildData): Unit = {}

  /**
   * This method is invoked when a member left the group.
   *
   * @param data The date of the member that left.
   */
  def memberLeft(data: ChildData): Unit = {}
}
