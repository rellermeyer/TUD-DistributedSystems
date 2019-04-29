package rover.rdo.comms.http

import rover.rdo.ObjectId
import rover.rdo.comms.{Server, ServerConfiguration}
import rover.rdo.conflict.ConflictedState
import rover.rdo.state.AtomicObjectState

/**
  * Simple implementation without persistence. All persistence is implemented
  * as HashMaps. These are not persisted between restarts of the server.
  * @tparam A The actual state type (i.e. `AtomicObjectState[A]`)
  */
class EphemeralServer[A <: Serializable] (
	val serverConfiguration: ServerConfiguration[A],
	var storage: Map[ObjectId, AtomicObjectState[A]]
) extends Server[A] {
	println(s"Ephemeral Server created with storage: ${storage}")

	override def create(): AtomicObjectState[A] = {
		val initial = AtomicObjectState.initial(serverConfiguration.initialStateValue)
		storage = storage.updated(initial.objectId, initial)

		return initial
	}

	override def get(objectById: ObjectId): Option[AtomicObjectState[A]] = {
//		println(s"Volatile Server, handling get request for ${objectById}")
//		println(s"   storage: ${storage}")

		val state = storage.get(objectById)
//		println(state.get.immutableState)

		// return
		state
	}

	override def accept(incomingVersion: AtomicObjectState[A]): Unit = {

		try {
//			println(s"Incoming: ${incomingVersion.immutableState}")

			val serverVersion = get(incomingVersion.objectId)
				.getOrElse({
					println("Cannot accept incoming version, no corresponding object/state known to the server")
					throw new Exception("Cannot accept incoming version, no corresponding object/state known to the server")
				})

//			println(s"   server ver: ${serverVersion.immutableState}")
			val conflictedState = ConflictedState.from(serverVersion, incomingVersion)
//			println("   conflicted state created")
			val mergeResult = serverConfiguration.conflictResolutionMechanism.resolveConflict(conflictedState)

//			println(s"  updating storage with updated: ${mergeResult.asAtomicObjectState.immutableState}")
			storage = storage updated(incomingVersion.objectId, mergeResult.asAtomicObjectState)
		}
		catch {
			case ex: Exception => println(ex)
		}
	}

	override def status(objectId: ObjectId): Unit = {
		// TODO: something
	}
}
