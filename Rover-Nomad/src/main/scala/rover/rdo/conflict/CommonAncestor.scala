package rover.rdo.conflict

import rover.rdo.{ObjectId, RdObject}
import rover.rdo.state.{AtomicObjectState, StateLog}

/**
  * Encapsulates the concept of a "common ancestor" RDO. That is, given two
  * RDO instances, the most recent state that both of them share.
  * This class is also responsible for determining the common ancestor between
  * the two instances.
  * @param one Some RDO
  * @param other Some other RDO
  */
class CommonAncestor[A <: Serializable](private val one: AtomicObjectState[A], private val other: AtomicObjectState[A]) extends AtomicObjectState[A] { // todo: fixme with a deferred state
	if (one.objectId != other.objectId) {
		throw new RuntimeException("Given AtomicObjectStates do not share same objectId. Not allowed to compare the two.")
	}

	override def objectId: ObjectId = one.objectId // one or other, doesn't matter

	// the state that is the ancestor
	lazy val state: AtomicObjectState[A] = determineTheCommonAncestor()
	
	private def determineTheCommonAncestor(): AtomicObjectState[A] = {
		
		var i = Option(one)
		var j = Option(other)
		
		// FIXME: There could be more than one parent, if the true ancestor is in an "incoming change" of a merge, the algo will not find it
		
		while(j.isDefined) {
			
			// for each j (latest to earliest) compare with all i's
			while(i.isDefined) {
				val a = j.get
				val b = i.get
				
				// same, common ancestor is either one of them equally
//				println(s"a = ${a}, b = ${b} :a == b? ${a == b}")
				// TODO: FIXME: HACKS!
				if (a.toString == b.toString) {
					return a
				}
				
				if (a.previous.contains(b)) {
					return b
				}
				
				if (b.previous.contains(a)) {
					return a
				}
				
				i = i.get.previous
			}
			
			i = Option(one) // reset i back to start
			j = j.get.previous // move one back
		}
		
		// TODO: typed exception
		throw new RuntimeException("Failed to determine a common ancestor")
	}
	
	/* delegate all AtomicObjectState's method to the actual ancestor (stored in state) */
	
	override val previous: Option[AtomicObjectState[A]] = {
		state.previous
	}

	override def toString: String = {
		state.toString
	}

	override def immutableState: A = {
		state.immutableState
	}

	override def log: StateLog[A] = {
		state.log
	}

	override def applyOp(operation: Op): AtomicObjectState[A] = {
		return state.applyOp(operation)
	}
}

object CommonAncestor {
	def from[A <: Serializable](serverVersion: RdObject[A], incomingVersion: RdObject[A]): CommonAncestor[A] = {
		return new CommonAncestor[A](serverVersion.state, incomingVersion.state)
	}

	def from[A <: Serializable](serverVersion: AtomicObjectState[A], incomingVersion: AtomicObjectState[A]): CommonAncestor[A] = {
		return new CommonAncestor[A](serverVersion, incomingVersion)
	}
}