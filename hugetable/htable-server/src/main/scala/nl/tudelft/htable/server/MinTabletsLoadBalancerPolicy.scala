package nl.tudelft.htable.server
import nl.tudelft.htable.core.{Node, Tablet}

import scala.collection.{Set, mutable}

/**
 * A load balancing policy minimizes the number of tablets per node.
 */
class MinTabletsLoadBalancerPolicy extends LoadBalancerPolicy {

  /**
   * The number of assignments per node.
   */
  val assignments = new mutable.HashMap[Node, Int]

  override def startCycle(nodes: Set[Node]): Unit = {
    assignments.addAll(nodes.map { node =>
      (node, 1)
    })
  }

  override def select(tablet: Tablet): Node = {
    val (node, count) = assignments.minBy(_._2)
    assignments(node) = count + 1
    node
  }

  override def endCycle(): Unit = {
    assignments.clear()
  }
}
