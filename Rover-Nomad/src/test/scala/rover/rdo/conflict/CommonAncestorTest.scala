package rover.rdo.conflict

import org.scalatest.FunSuite
import rover.rdo.state.AtomicObjectState

class CommonAncestorTest extends FunSuite {
	
	test("determining initial state as common works") {
		val initial = AtomicObjectState.initial[List[String]](List())
		
		val next = initial.applyOp(s => s :+ "Henk")
		
		val common = CommonAncestor.from(initial, next).state
		
		assert(initial == common)
	}
	
}
