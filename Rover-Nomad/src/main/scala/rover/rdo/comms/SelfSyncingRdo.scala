package rover.rdo.comms

import rover.rdo.RdObject
import rover.rdo.comms.SyncDecision.{SyncDecision, sync}
import rover.rdo.state.AtomicObjectState

//import rover.rdo.comms.SyncDecision._


//class RdoSyncType[T]
//object RdoSyncType {
//	implicit object Automatic[X <: AutomaticSync] extends RdoSyncType[AutomaticSync]
//	implicit object Manual extends RdoSyncType[ManualSync]
//}


/**
  * Augments the RdObject with ability to self-sync. That is, enrich the RdObject's
  * API methods to manage the syncing of the RdObject.
  * @tparam A
  */
trait SelfSyncingRdo[A <: Serializable] {
	// This is trait appliable on RdObjects. We want access to RdObject internals from here
	this: RdObject[A] =>

// TODO: possible to simply have the version use SelfSyncingRdo to reconcile incoming version
//  and we want single point holding the configuration for RdObjects, might be good enough for now
//	def conflictResolutionMechanism: ConflictResolutionMechanism[A]

	// TODO: include diffs for usability. Just diffs would include
	//  merges, and that is not something that the user here would want to
	//  deal with here. A merge log record is not very descriptive, it's a sudden before-after

	/**
	  * <p>Gets called before a sync of the Rdo's state is executed.</p><br/>
	  * <p>The implementation can prevent the sync from taking place.</p>
	  * @param currentState The current state of the Rdo TODO: decide A or Atomic[A]?
	  * @return decision if sync should take place
	  */
	def beforeSync(currentState: A): SyncDecision

	/**
	  * Called after a sync has been done
	  * TODO: different param, one that encompasses the post-sync better
	  *  something along the lines of the MergeConflict type but different
	  * @param newState The state-after-sync, but we might want it to be the wrapping RdObject
	  */
	def afterSync(newState: A)

	final def requestSync(): Unit = {
		val syncDecision = beforeSync(this.state.immutableState)
//		println(s"Sync requested, decision: ${syncDecision}")

		if (syncDecision == sync) {
//			println("Chat is going to sync")
			// push local, the server will reconcile the incoming version with own
			pushLocalVersion(this.state)

			// fetch the reconciled version
			val serverVersion = fetchServerVersion()

			// todo: detect if state has changed compared to pushed?

			// Set local version to that reconceiled from the server
			this.state = serverVersion

			// Notify sync/update/fetch has taken place
			afterSync(serverVersion.immutableState)

			// TODO: decide, give new version before or after updating this.state?
			//  furthermore, we can let the afterSync decide if it wants to accept upstream
		}
//		else {
//			println(" :( decision wasn't sync???")
//		}
	}

	protected def fetchServerVersion(): AtomicObjectState[A]
	protected def pushLocalVersion(localVersion: AtomicObjectState[A])
}

object SyncDecision extends Enumeration {
	type SyncDecision = Boolean
	val sync = true
	val dont = false
}

//class Henk[A,C, D <: SelfSyncingRdo[A]]() {
//	def sjaak(): RdObject[A] with D = {
//
//	}
//}

//class RdoSyncX[A](val a: String) extends RdoSync[A] {
//}
//
//
//class AllowedToSync extends SyncDecision {
//
//}
//
//trait AutomaticSync[A] extends RdoSync[A] {
//
//}
//
//trait ManualSync[A] extends RdoSync[A] {
//
//}
