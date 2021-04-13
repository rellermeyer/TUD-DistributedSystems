package CRUSH.controller

import CRUSH.utils.crushmap._
import org.slf4j.Logger

import java.time.Instant
import scala.collection.mutable.ListBuffer

object Rootstore {

  var map: CrushMap                                    = CrushMap(None, Nil)
  var configMap: CrushMap                              = CrushMap(None, Nil)
  var aliveNodes: ListBuffer[(Int, Long, String, Int)] = new ListBuffer[(Int, Long, String, Int)]()
  var statusMap: Map[Boolean, Set[Int]]                = Map[Boolean, Set[Int]]()
  var maxTimeOut                                       = 30 //seconds
  var placementRule: PlacementRule                     = PlacementRule(Nil)

  /**
   * Add/Update alive nodes to the alive OSD list
   *
   * @param id      - the id of the OSD
   * @param address - the address to contact the OSD on
   * @return aliveNodes, but shouldn't be used
   */
  def addAlive(id: Int, address: String, space: Int, initialized: Boolean, optionalTime: Option[Long] = None)(implicit
    logger: Logger
  ): Boolean = {
    // updates are not allowed on tuples, so remove and re-add
    val maybeTuple = this.aliveNodes.find(_._1 == id)
    val time = optionalTime match {
      case Some(time) => time
      case None       => Instant.now().getEpochSecond
    }
    maybeTuple match {
      case None =>
        logger.info("Received alive message from new node!")
        this.aliveNodes += ((id, time, address, space))
      case Some(a @ (_, _, _, oldStorage)) =>
        logger.info(s"Updating $id's entry")
        if (oldStorage != space) {
          logger.debug(s"$id's storage differs from previous: ${oldStorage - space}'")
        }
        this.aliveNodes -= a
        this.aliveNodes += ((id, time, address, space))
    }
    this.statusMap = Map(
      initialized  -> this.statusMap.getOrElse(initialized, Set()).union(Set(id)),
      !initialized -> this.statusMap.getOrElse(!initialized, Set()).removedAll(Set(id))
    )
    maybeTuple.isDefined
  }

  def isInitialized(expectedNumOSDs: Int): Boolean = this.statusMap.getOrElse(true, Set()).size == expectedNumOSDs

  /**
   * Remove all the nodes that have not send a heartbeat in the last maxTimeOut seconds
   */
  def purgeDeadNodes(): Unit = {
    val minimalTime = Instant.now().getEpochSecond - maxTimeOut
    aliveNodes = aliveNodes.filter(tuple => {
      tuple._2 > minimalTime
    })
  }

  def filterMap(): Unit = {
    val aliveIds = aliveNodes.map(n => (n._1, n._4)) // also update space
    configMap.root match {
      case None => None
      case Some(rootNode) =>
        val newRoot = filterNode(rootNode, aliveIds)
        newRoot match {
          case Some(value) => map = CrushMap(Some(value), Nil) // because root is not assignable
          case None        => map = CrushMap(None, Nil)        // empty crushmap
        }
    }
  }

  def filterNode(node: Node, aliveIds: ListBuffer[(Int, Int)]): Option[Node] = node match {
    case OSD(address, w, id, _) =>
      val aliveNodes = aliveIds.filter(_._1 == id)
      if (aliveNodes.nonEmpty) Some(OSD(address, w, id, aliveNodes.head._2)) else None
    case Bucket(children, bucketType, id) =>
      val optionalChildren = children.map(child => filterNode(child, aliveIds))
      val aliveChildren    = optionalChildren.flatten
      if (aliveChildren.isEmpty) None else Some(Bucket(aliveChildren, bucketType, id))

  }
}
