package rover.rdo.conflict.resolve

import rover.rdo.conflict.ConflictedState
import rover.rdo.state.{AtomicObjectState, BasicAtomicObjectState, MergeOperation}

/**
  * An interface for any mechanism that can resolve
  * a state conflict
 *
  * @tparam A The state implementation type
  */
trait ConflictResolutionMechanism[A <: Serializable] extends Serializable {
	/**
	  * <p>
	  *     Resolves the conflict contained in the ConflictedState argument
	  *     Currently assumes the code will resolve the conflict, and will return
	  *     once the conflict has been successfully resolved.
	  * </p>
	  * <br/>
	  * <p>
	  *     <b>Future TODO:</b> Allow deferred resolving. That is, the server implementation
	  *     can request the corresponing client to resolve locally (client application
	  *     asking the user to resolve manually).
	  *
	  *     <p>
	  *       Two possible implementations:
	  *       <li>back-off or fail the conflict resolving and let the framework signal this back to client (preferred, implementation complexity wise)</li>
	  *       <li>somehow let the the method ask the client directly and wait until resolved version is returned</li>
	  *     </p>
	  * </p>
	  * @param conflictedState The conflicted state, contains a common ancestor, the server and incoming versions
	  * @return The "merged" state
	  */
	def resolveConflict(conflictedState: ConflictedState[A]): ResolvedMerge[A]
}

case class ResolvedMerge[A <: Serializable](conflictedState: ConflictedState[A], resultingState: A, implicit val conflictResolutionMechanism: ConflictResolutionMechanism[A]) {
	lazy val asAtomicObjectState: AtomicObjectState[A] = {
		val mergeOperationExectured = new MergeOperation[A](conflictedState.serverVersion, conflictedState.incomingVersion, conflictResolutionMechanism)
		new BasicAtomicObjectState[A](conflictedState.serverVersion.objectId, resultingState, conflictedState.serverVersion.log.appended(mergeOperationExectured))
	}
}