package CRUSH.utils.crushmap

import java.util.Objects
import CRUSH.CBorSerializable
import com.fasterxml.jackson.annotation.{ JsonSubTypes, JsonTypeInfo }

import scala.collection.mutable.ListBuffer
import scala.util.Random

case class CrushMap(root: Option[Node], levels: List[HierarchyLevel]) extends CBorSerializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[Leaf], name = "leaf"),
    new JsonSubTypes.Type(value = classOf[Rack], name = "rack"),
    new JsonSubTypes.Type(value = classOf[Row], name = "row"),
    new JsonSubTypes.Type(value = classOf[Room], name = "room"),
    new JsonSubTypes.Type(value = classOf[Datacenter], name = "datacenter")
  )
)
trait HierarchyLevel extends CBorSerializable

case class Leaf() extends HierarchyLevel

case class Rack() extends HierarchyLevel

case class Row() extends HierarchyLevel

case class Room() extends HierarchyLevel

case class Datacenter() extends HierarchyLevel

/**
 * Class of bucket types
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[Uniform], name = "uniform"),
    new JsonSubTypes.Type(value = classOf[Straw], name = "straw")
  )
)
abstract class BucketType extends CBorSerializable {

  /**
   * Select children from a single bucket
   * @param amount - number of children to select
   * @param children - list of children
   * @param size - size of object
   * @param random - random number generator based on hash of object
   * @return
   */
  def selectChildren(amount: Int, children: List[Node], size: Int)(implicit random: Random): List[Node]
}

/**
 * Node class for buckets and OSDs
 * @param weight - weight of OSD or total weight of children
 * @param space - weight of OSD or maximum space of children
 * @param id - id of bucket/OSD
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[OSD], name = "osd"),
    new JsonSubTypes.Type(value = classOf[Bucket], name = "bucket")
  )
)
abstract class Node(val weight: Double, var space: Int, val id: Int) extends CBorSerializable {
  require(weight >= 0.0)
  require(space >= 0)

  /**
   * Function to change space of an OSD with a specific id. Used for OSD check placement
   * @param id - id to change space of
   * @param space - space to add to the OSD
   */
  def changeSpace(id: Int, space: Int): Unit

  /**
   * Recursively compute the space of OSDs and buckets. Used for OSD check placement
   * @return maximal space of children or OSD space
   */
  def updateSpace(): Int
}

/**
 * Uniform bucket
 */
case class Uniform() extends BucketType {

  /**
   * Select children from a Uniform bucket
   * @param amount - number of children to select
   * @param children - list of children
   * @param size - size of object
   * @param random - random number generator based on hash of object
   *  @return List of Nodes
   */
  override def selectChildren(amount: Int, children: List[Node], size: Int)(implicit random: Random): List[Node] = {

    var childrenList: ListBuffer[Node] =
      children.filter(n => n.space >= size).to(ListBuffer)
    if (amount >= childrenList.length) {
      return childrenList.toList
    }

    var output: ListBuffer[Node] = ListBuffer[Node]()
    Range(0, amount).foreach(_ => {
      // when the length changes, the random generator selects a completely different node, therefore is the change in placement around 100%
      val index: Int = random.nextInt(childrenList.length)
      val selected   = childrenList(index)
      output += selected
      childrenList -= selected
    })
    output.toList
  }
}

/**
 * Straw bucket type
 */
case class Straw() extends BucketType {
  val maxStraw: Int = 10000

  /**
   * Straw bucket selection algorithm
   * @param amount - number of children to select
   * @param children - list of children
   * @param size - size of object
   * @param random - random number generator based on hash of object
   *  @return list of nodes that were selected
   */
  override def selectChildren(amount: Int, children: List[Node], size: Int)(implicit random: Random): List[Node] = {
    if (amount >= children.length) {
      return children
    }

    var childrenList: ListBuffer[Node] = children.filter(n => n.space >= size).to(ListBuffer)
    val x                              = random.nextInt(Integer.MAX_VALUE)
    var output: ListBuffer[Node]       = ListBuffer[Node]()

    0 until amount foreach (r => {
      // A node can be removed or added at the start of the list, this would shuffle the whole bucket, therefore is the
      // selection not dependent on the ordering, but on the id of the bucket, the replication and a number coming from
      // the hash of the object.
      val straws = childrenList.map(c => Tuple2(new Random(Objects.hash(c.id, r, x)).nextInt(maxStraw) * c.weight, c))
      val select = straws.sortBy(_._1).reverse.head._2
      output += select
      childrenList -= select
    })
    output.toList
  }
}

/**
 * Bucket node
 * @param children - the children of this bucket
 * @param bucketType - type of bucket, Straw() or Uniform()
 * @param d - the id of the bucket(used for straw selection)
 */
case class Bucket(children: List[Node], bucketType: BucketType, d: Int)
    extends Node(children.map(n => n.weight).sum, children.map(n => n.space).max, d) {

  /**
   * Update the space of this bucket
   *  @return maximal space of children or OSD space
   */
  def updateSpace(): Int = {
    // max because when no children have enough space left for the object, then the bucket also can't store it, the object cannot be split
    space = children.map(_.updateSpace()).max
    space
  }

  /**
   * Change space of children with that id
   * @param id - id to change space of
   * @param space - space to add to the OSD
   */
  override def changeSpace(id: Int, space: Int): Unit = {
    children.foreach(_.changeSpace(id, space))
  }
}

/**
 * OSD node
 * @param address - akka address
 * @param w - weight of the OSD
 * @param d - id of the OSD
 * @param sp - space of the OSD
 */
case class OSD(address: String, w: Double, d: Int = 1, sp: Int = 10) extends Node(w, sp, d) {

  /**
   * If this OSD has the id, then change the space
   * @param id - id to change space of
   * @param space - space to add to the OSD
   */
  override def changeSpace(id: Int, space: Int): Unit = {
    if (id == this.id) {
      this.space += space
    }
  }

  /**
   * Get the space of this OSD
   *  @return OSD space
   */
  override def updateSpace(): Int = this.space
}
