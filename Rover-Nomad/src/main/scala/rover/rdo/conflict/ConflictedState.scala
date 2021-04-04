package rover.rdo.conflict

import rover.rdo.RdObject
import rover.rdo.state.AtomicObjectState

class ConflictedState[A <: Serializable] private (val serverVersion: AtomicObjectState[A], val incomingVersion: AtomicObjectState[A]) {
	lazy val commonAncestor: CommonAncestor[A] = {
		// return
		new CommonAncestor[A](serverVersion, incomingVersion)
	}

	/**
	  * The changes that were made in the "incoming" version since
	  * the common ancestor
	  * @return The changes from ancestor to "incoming"
	  */
	lazy val changesIncomingRelativeToCommonAncestor: DiffWithAncestor[A] = {
		// return
		diffWithAncestor(incomingVersion)
	}

	/**
	  * The changes that are in the server's version relative to the
	  * common ancestor
	  * @return The changes from ancestor to "server"
	  */
	lazy val changesOnServerRelativeToCommonAncestor: DiffWithAncestor[A] = {
		// return
		diffWithAncestor(serverVersion)
	}

	def diffWithAncestor(childState: AtomicObjectState[A]): DiffWithAncestor[A] = {
		val commonAncestor = this.commonAncestor.state
		return new DiffWithAncestor[A](childState, commonAncestor)
	}
}

object ConflictedState {
	def from[A <: Serializable](serverVersion: RdObject[A], incomingVersion: RdObject[A]): ConflictedState[A] = {
		return new ConflictedState[A](serverVersion.state, incomingVersion.state)
	}

	def from[A <: Serializable](serverVersion: AtomicObjectState[A], incomingVersion: AtomicObjectState[A]): ConflictedState[A] = {
		return new ConflictedState[A](serverVersion, incomingVersion)
	}
}
