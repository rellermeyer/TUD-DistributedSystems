package rover.rdo.comms

import rover.rdo.ObjectId
import rover.rdo.conflict.resolve.ConflictResolutionMechanism
import rover.rdo.state.AtomicObjectState

/**
  * A server handles the reconciliation of incoming versions of states/objects
  * and being a repository for said states/objects. <br/><br/>
  *
  * Each "server" implementation only handles one type of object/state.
  * That is, if multiple states/objects are required, multiple "servers"
  * are required as well.
  * @tparam A
  */
abstract class Server[A <: Serializable] {

	/**
	  * <p>
	  *     Requests a new object/state to be created
	  * </p>
	  * @return The new object/state
	  */
	def create(): AtomicObjectState[A]

	/**
	  * <p>
	  *     Request the most recent object's state
	  *     from the server.
	  * </p>
	  * @param objectId The id of the RdObject/State
	  */
	def get(objectId: ObjectId): Option[AtomicObjectState[A]]

	/**
	  * <p>
	  *     Present the state to the server for the changes to be included
	  *     in the master version.
	  * </p><br />
	  * <p>
	  *     It is up to the server implementation
	  *     how, if at all, to reconcile any divergence of state between the
	  *     incoming and the local version(s) it maintains.
	  * </p>
	  * @param state The incoming state that is to be presented to server
	  */
	def accept(incomingState: AtomicObjectState[A]) // todo return some kind of status/result

	/**
	  * <p>
	  *     Request the status of the object's state.
      * </p>
	  * @param objectId The id of the object/state of which the status is requested
	  */
	def status(objectId: ObjectId) // TODO: return type with relevant info (latest version?)
}

final case class ServerConfiguration[A <: Serializable](
	initialStateValue: A,
	conflictResolutionMechanism: ConflictResolutionMechanism[A]
)
