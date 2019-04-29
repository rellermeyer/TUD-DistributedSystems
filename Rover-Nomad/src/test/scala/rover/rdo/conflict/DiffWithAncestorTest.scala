package rover.rdo.conflict

import org.scalatest.FunSuite
import rover.rdo.state.AtomicObjectState

class DiffWithAncestorTest extends FunSuite {
	test("diff works when common ancestor is initial state and only one change") {
		val initial = AtomicObjectState.initial[List[String]](List())
		val modified = initial.applyOp(s => s :+ "Henk")

		val diff = new DiffWithAncestor(modified, new CommonAncestor(initial, modified))

		assert(diff.asList.size == 1)
		assert(diff.asList.head == modified.log.asList.reverse.head)
	}

	test("diff works when states are equal") {
		val initial = AtomicObjectState.initial[List[String]](List())
		val modified = initial.applyOp(s => s :+ "Henk")

		val diff = new DiffWithAncestor(modified, modified)

		assert(diff.asList.size == 0)
	}

	test("diff works when states are equal with Comparable data-structures") {
		val initial = AtomicObjectState.initial[List[String]](List("Piet"))
		val commonAncestor = new CommonAncestor(initial, initial)
		val diff = new DiffWithAncestor(initial, commonAncestor)

		assert(diff.asList.size == 0)
	}


	test("diff works when states are equal with nested Comparable data-structures") {
		val initial = AtomicObjectState.initial[List[List[String]]](List(List("Piet")))
		val commonAncestor = new CommonAncestor(initial, initial)
		val diff = new DiffWithAncestor(initial, commonAncestor)

		assert(diff.asList.size == 0)
	}
	
	test("diff works with longer scenarios") {
		val initial = AtomicObjectState.initial[List[String]](List())
		val checkpoint1 = initial.applyOp(s => s :+ "1")
				.applyOp(s => s :+ "2")
				.applyOp(s => s :+ "3")

		val probedOp = (s: List[String]) => s :+ "4"

		val checkpoint2 = checkpoint1.applyOp(probedOp)
	            .applyOp(s => s :+ "5")
	            .applyOp(s => s :+ "6")

		val commonAncestor = new CommonAncestor[List[String]](checkpoint1, checkpoint2)
		val diff = new DiffWithAncestor[List[String]](checkpoint2, commonAncestor)

		assert(commonAncestor == checkpoint1)
		assert(diff.asList.head.parent.get == checkpoint1)
		assert(diff.asList.size == 3)
	}
}
