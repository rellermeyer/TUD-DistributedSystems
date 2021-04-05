package rover.rdo

import java.util.concurrent.TimeUnit

import rover.rdo.comms.SyncDecision.SyncDecision
import rover.rdo.comms.{SelfSyncingRdo, Session}
import rover.rdo.state.AtomicObjectState

import scala.concurrent.Await
import scala.concurrent.duration.Duration

///**
//  * This is a sort of combined repository & factory for RdObjects
//  * Currently every RdObject is implemented with AtomicStateObjects
//  *
//  * @param server
//  * @tparam A
//  * @tparam C
//  * @tparam RDO
//  */
//abstract class RoverApplicationBootstrap[A <: Serializable, C, RDO <: RdObject[A]] (
//		private val client: Client[C, A],
//		private val credentials: C
//) {
//	// TODO: decorate Session s.t. it self-updates when needed and expired
//	private def session: Session[C, A] = {
//		return client.createSession(credentials)
//	}
//
//	def checkoutObject(objectId: ObjectId): Unit = {
//		session.importRDO(objectId)
//	}
//
//
//}

class RdObjectWithHttpSelfSync[A <: Serializable, C](
	val _beforeSync: A => SyncDecision,
	val _afterSync: A => Unit,
	val session: Session[C, A]
) extends SelfSyncingRdo[A] {
	this: RdObject[A] =>

	/**
	  * <p>Gets called before a sync of the Rdo's state is executed.</p><br/>
	  * <p>The implementation can prevent the sync from taking place.</p>
	  *
	  * @param currentState The current state of the Rdo TODO: decide A or Atomic[A]?
	  * @return decision if sync should take place
	  */
	override def beforeSync(currentState: A): SyncDecision = {
		_beforeSync(currentState)
	}

	/**
	  * Called after a sync has been done
	  * TODO: different param, one that encompasses the post-sync better
	  * something along the lines of the MergeConflict type but different
	  *
	  * @param newState The state-after-sync, but we might want it to be the wrapping RdObject
	  */
	override def afterSync(newState: A): Unit = {
		_afterSync(newState)
	}

	override protected def fetchServerVersion(): AtomicObjectState[A] = {
		val futureResult = session.importRDO(this.state.objectId)
		val actual = Await.result(futureResult, Duration.create(1, TimeUnit.MINUTES))

		return actual
	}

	override protected def pushLocalVersion(localVersion: AtomicObjectState[A]): Unit = {
		session.exportRDOwithState(localVersion.objectId.asString)
	}
}