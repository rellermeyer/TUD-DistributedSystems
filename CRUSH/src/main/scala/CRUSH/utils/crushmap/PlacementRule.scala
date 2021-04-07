package CRUSH.utils.crushmap

import CRUSH.CBorSerializable
import com.fasterxml.jackson.annotation.{ JsonSubTypes, JsonTypeInfo }

/**
 * Placement rule to select nodes to store the objects on
 * @param steps - list of placement steps
 */
case class PlacementRule(steps: List[PlacementRuleStep]) extends CBorSerializable

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Array(
    new JsonSubTypes.Type(value = classOf[Select], name = "select"),
    new JsonSubTypes.Type(value = classOf[Emit], name = "emit")
  )
)
sealed trait PlacementRuleStep extends CBorSerializable

/**
 * Select step
 * @param n - number of nodes to select
 * @param h - hierarchy level of this step(unused)
 */
final case class Select(n: Int, h: HierarchyLevel) extends PlacementRuleStep

/**
 * Step to return the list of nodes selected
 * Should be OSDs when same number of select steps as bucket levels
 */
final case class Emit() extends PlacementRuleStep
