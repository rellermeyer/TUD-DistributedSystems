package rover.rdo.comms

import rover.rdo.ObjectId
import rover.rdo.state.AtomicObjectState

/**
  * Interface for the clients. Provides primitives to exchange
  * RdObject's state with the server
  * @tparam A The state itself
  */
abstract class Client[A <: Serializable] {
	def created(): AtomicObjectState[A]

	def fetch(objectId: ObjectId): AtomicObjectState[A]

	def push(state: AtomicObjectState[A])
}
