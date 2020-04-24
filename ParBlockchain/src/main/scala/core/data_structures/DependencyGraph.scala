package core.data_structures

import scala.collection.mutable

class DependencyGraph(graphID: Int) {
  private val ID: Int = graphID
  private val graph: mutable.HashMap[Transaction, mutable.Set[Transaction]] =
    new mutable.HashMap[Transaction, mutable.Set[Transaction]]()
      { override def default(key: Transaction): mutable.Set[Transaction] = mutable.Set[Transaction]()}
  private val predecessors: mutable.HashMap[Transaction, mutable.Set[Transaction]] =
    new mutable.HashMap[Transaction, mutable.Set[Transaction]]()

  def addEdge(transaction: Transaction, other: Transaction): Unit = {
    val otherSet = graph.getOrElse(transaction, mutable.Set[Transaction]())
    otherSet.add(other)
    graph += (transaction -> otherSet)

    if (graph(other).isEmpty) {
      graph += (other -> mutable.Set[Transaction]())
    }

    val predecessorSet = predecessors.getOrElse(other, mutable.Set[Transaction]())
    predecessorSet.add(transaction)
    predecessors += (other -> predecessorSet)
  }

  def addNode(transaction: Transaction): Unit = {
    if (!graph.contains(transaction)) {
      graph += (transaction -> mutable.Set[Transaction]())
    }
  }

  def getGraph: mutable.HashMap[Transaction, mutable.Set[Transaction]] = graph

  def getPredecessors(transaction: Transaction): mutable.Set[Transaction] = {
    predecessors.getOrElse(transaction, mutable.Set[Transaction]())
  }

  def isCyclic: Boolean = {
    val visited = new mutable.HashMap[Transaction, Boolean]()
    val recStack = new mutable.HashMap[Transaction, Boolean]()

    for (n <- graph) {
      visited += (n._1 -> false)
      recStack += (n._1 -> false)
    }
    var res = false
    for (n <- graph) {
      if (isCyclicUtil(n._1, visited, recStack)) {
        res = true
      }
    }
    res
  }

  private def isCyclicUtil(node: Transaction,
                           visited: mutable.HashMap[Transaction, Boolean],
                           recStack: mutable.HashMap[Transaction, Boolean]): Boolean = {
    visited(node) = true
    recStack(node) = true

    var res = false
    for (n <- graph(node)) {
      if (!visited(n) && isCyclicUtil(n, visited, recStack)) {
        res = true
      } else if (recStack(n)) {
        res = true
      }
    }
    if (!res) {
      recStack(node) = false
    }
    res
  }
}
