package ledger

import core.applications.Application
import core.data_structures.{DependencyGraph, Transaction}
import core.operations.Operation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.collection.immutable

class GraphSpec extends AnyFlatSpec with Matchers {
  val app: Application.Value = Application.A

  "Graph" should "be able to contain vertices" in {
    val g = new DependencyGraph(0)
    val t_1 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    val t_2 = new Transaction(0, Operation.transfer, "a", Some("c"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    val t_3 = new Transaction(0, Operation.transfer, "c", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    g.addEdge(t_1, t_2)
    g.addEdge(t_2, t_3)
    g.addEdge(t_1, t_3)
    g.addEdge(t_1, t_3)

    g.getGraph.size shouldEqual 3
    g.getPredecessors(t_3).size shouldEqual 2
    g.getPredecessors(t_1).size shouldEqual 0
  }

  "Graph" should "be able to detect cycles" in {
    val g = new DependencyGraph(0)
    val t_1 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    val t_2 = new Transaction(0, Operation.transfer, "a", Some("c"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    val t_3 = new Transaction(0, Operation.transfer, "c", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    g.addEdge(t_1, t_2)
    g.addEdge(t_2, t_3)
    g.addEdge(t_1, t_3)

    g.isCyclic shouldEqual false

    g.addEdge(t_3, t_1)

    g.isCyclic shouldEqual true
  }

  "Graph" should "return correct predecessors" in {
    val g = new DependencyGraph(0)
    val t_1 = new Transaction(0, Operation.transfer, "a", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    val t_2 = new Transaction(0, Operation.transfer, "a", Some("c"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    val t_3 = new Transaction(0, Operation.transfer, "c", Some("b"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    g.addEdge(t_1, t_2)
    g.addEdge(t_2, t_3)
    g.addEdge(t_1, t_3)

    g.getPredecessors(t_3).size shouldEqual 2
    assert(g.getPredecessors(t_3).contains(t_1))
    assert(g.getPredecessors(t_3).contains(t_2))

    val t_4 = new Transaction(0, Operation.transfer, "d", Some("e"), immutable.Seq[String](), immutable.Seq[String](),1, app)
    g.getPredecessors(t_4).size shouldEqual 0

  }
}
