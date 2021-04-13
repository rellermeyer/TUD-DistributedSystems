package CRUSH

import CRUSH.utils.crushmap._

import scala.annotation.tailrec
import scala.util.Random

/**
 * Main crush algorithm implementation.
 */
object Crush {

  /**
   * Crush function to get the placement of an object
   * @param hash - The object has for the random
   * @param map - the crushmap
   * @param rule - the placement rule to select the OSDs
   * @param size - the size of the object
   * @return List of Nodes, should be OSDs
   */
  def crush(hash: Int, map: CrushMap, rule: PlacementRule, size: Int = 1): List[Node] = {
    val root = map match {
      case CrushMap(Some(root), _) => root
      case CrushMap(None, _)       => return Nil
    }

    val steps  = rule.steps
    val random = new Random(hash)

    applySteps(List(root), steps, size, random)
  }

  /**
   * Special function for the OSD to check its own files, compared with the previous placement
   * @param hash - The object hash for the random
   * @param map - the crushmap
   * @param rule - rules for crushing
   * @param size - the size of the object
   * @param oldPlacement - old placement, needed for checking and space requirements
   * @return tuple( hasChanges boolean, (new) placement)
   */
  def osdCheckPlacement(
    hash: Int,
    map: CrushMap,
    rule: PlacementRule,
    size: Int = 1,
    oldPlacement: List[Node]
  ): (Boolean, List[Node]) = {
    // the osd can be full with some objects. The crush algorithm then skips that osd(even (almost)full buckets)
    // therefore the space of the OSDs needs to be changed before checking whether the file still needs to be there
    // and after running it, the size should decrease again

    val root = map match {
      case CrushMap(Some(root), _) => root
      case CrushMap(None, _)       => return (false, Nil)
    }

    // Add size to old placement OSDs
    oldPlacement.foreach {
      case OSD(_, _, id, _) => root.changeSpace(id, size)
      case Bucket(_, _, _)  => () // this should never happen
    }

    // update the available space
    root.updateSpace()

    // run crush
    val output = crush(hash, map, rule, size)

    // check if it is same as before
    var changed = false
    if (output.length != oldPlacement.length) {
      // updated
      changed = true
    }
    val changes = output.filter(!oldPlacement.contains(_))
    if (changes.nonEmpty) {
      changed = true
    }
    // Remove size from old placement osd
    oldPlacement.foreach {
      case OSD(_, _, id, _) => root.changeSpace(id, -size)
      case Bucket(_, _, _)  => () // this should never happen
    }

    // update available space
    root.updateSpace()
    // return
    (changed, output)
  }

  /**
   * Apply all the placement steps
   * @param input - the list of nodes(buckets/OSDs) to recursively run the algorithm on
   * @param steps - placement rule list
   * @param size - size of the object
   * @param random - random generator based on hash of object
   * @return list of nodes
   */
  @tailrec
  def applySteps(input: List[Node], steps: List[PlacementRuleStep], size: Int, random: Random): List[Node] =
    steps match {
      case s :: t =>
        applySteps(applyStep(input, s, size, random), t, size, random)
      case Nil => input
    }

  /**
   * A single placement step
   * @param input - list of nodes, rule is run on each of them and result is merged into single list
   * @param step - placement step to execute
   * @param size - size of object
   * @param random - random number generator
   * @return list of nodes
   */
  def applyStep(input: List[Node], step: PlacementRuleStep, size: Int, random: Random): List[Node] = step match {
    case Select(n, _) => select(n, input, size, random)
    case Emit()       => input
  }

  /**
   * Select n amount of children from each bucket
   * @param n - number of children to select from each bucket
   * @param input - list of buckets
   * @param size - size of object
   * @param random - random number generator based on hash of object
   * @return list of Nodes
   */
  def select(n: Int, input: List[Node], size: Int, random: Random): List[Node] = {
    input.flatMap((node: Node) => randomChildren(n, node, size, random))
  }

  /**
   * Select children from a bucket
   * @param n - amount to select
   * @param node - bucket to select from
   * @param size - size of the object
   * @param random - random number generator
   * @return
   */
  def randomChildren(n: Int, node: Node, size: Int, random: Random): List[Node] = node match {
    case Bucket(children, bucketType, _) => bucketType.selectChildren(n, children, size)(random)
    case _                               => throw new IllegalArgumentException() // cannot select from an OSD
  }
}
