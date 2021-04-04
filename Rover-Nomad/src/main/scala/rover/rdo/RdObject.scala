package rover.rdo

import rover.rdo.state.AtomicObjectState

import scala.async.Async.async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RdObject[A <: Serializable](var state: AtomicObjectState[A]) {

	// same:
	//FIXME: use hashes instead of Longs/Strings?
	//TODO: "is up to date" or "version" methods

	protected final def modifyState(op: AtomicObjectState[A]#Op): Unit = {
		state = state.applyOp(op)

		/** FIXME: Trigger onStateModified? I guess we don't want to make this function async too.\
		  * Proposing original division into two sub RDO types:  AutoSyncRdObject & ManualRdObject.\
		  * The former works with async methods and refreshes automatically (push), the other is manual
		  * (pull). The async one might require raising events for everything.
		  */
		//onStateModified(state)
	}

	// FIXME
	protected def onStateModified(oldState: AtomicObjectState[A]): Future[Unit] = {
		async {

		}
	}

	protected final def immutableState: A = {
		return state.immutableState
	}

	override def toString: String = {
		state.toString
	}
}