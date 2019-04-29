package rover.rdo.state

import rover.rdo.ObjectId

trait AtomicObjectState[A <: Serializable] extends Serializable {
	type Op = A => A

	def objectId: ObjectId

	def immutableState: A

	def log: StateLog[A]
	
	def previous: Option[AtomicObjectState[A]]
	
	def applyOp(operation: Op): AtomicObjectState[A]

	override def equals(obj: Any): Boolean = {
		obj match {
			case other: AtomicObjectState[A] => this.immutableState == other.immutableState
			case _ => false
		}
	}
}

// TODO: make ctor private?
@SerialVersionUID(54376L)
class InitialAtomicObjectState[A <: Serializable] (identityState: A) extends AtomicObjectState[A] {
	
	override val objectId: ObjectId = {
		val newId = ObjectId.generateNew()
//		println(s"New initial atomic object state, with id: ${newId}")
		newId
	}

	override def immutableState: A = identityState
	
	override val log: StateLog[A] = StateLog.forInitialAtomicObjectState(this)
	
	
	override val previous: Option[AtomicObjectState[A]] = Option.empty
	
	override def applyOp(operation: Op): AtomicObjectState[A] = {
//		val resultingState = operation.apply(immutableState)
//		return new BasicAtomicObjectState[A](this.objectId, resultingState, log)
		
		// Operation must apply itself to the state
		// but we want the state to take in the operations
		// so that the framework can record the op
		val result = operation.apply(this.immutableState)
		
		// Record the operation in the Log
		val updatedLog = log.appended(OpAppliedRecord(operation, this))
		
		return new BasicAtomicObjectState[A](this.objectId, result, updatedLog)
	}
}

@SerialVersionUID(75436L)
class BasicAtomicObjectState[A <: Serializable] (val objectId: ObjectId, val immutableState: A, val log: StateLog[A]) extends AtomicObjectState[A] {

	def applyOp(operation: Op): AtomicObjectState[A] = {
		// Operation must apply itself to the state
		// but we want the state to take in the operations
		// so that the framework can record the op
		val result = operation.apply(this.immutableState)

		// Record the operation in the Log
		val updatedLog = log.appended(OpAppliedRecord(operation, this))

		return new BasicAtomicObjectState[A](this.objectId, result, updatedLog)
	}
	
	lazy override val previous: Option[AtomicObjectState[A]] = {
		log.asList.reverse.head.parent
	}

	override def toString: String = {
		immutableState.toString
	}
}

object AtomicObjectState {
	/**
	  * <p><b>Currently only the server should ever access this method</b></p><br/>
	  * Creates a new, fresh AtomicObjectState initialized with the
	  * @param value
	  * @tparam A
	  * @return
	  */
	def initial[A <: Serializable](value: A): AtomicObjectState[A] = {
		return new InitialAtomicObjectState[A](value)
	}

//	def fromLog[A](log: StateLog[A]): AtomicObjectState[A] = {
//		return log.latestState.resultingAtomic
//	}

	def byApplyingOp[A <: Serializable](stateFrom: AtomicObjectState[A], op: AtomicObjectState[A]#Op): AtomicObjectState[A] = {
		val resultingState = op.apply(stateFrom.immutableState)
		val appendedLog = stateFrom.log.appended(new OpAppliedRecord[A](op, stateFrom))

		val resultingAtomicState = new BasicAtomicObjectState[A](stateFrom.objectId, resultingState, appendedLog)

		return resultingAtomicState
	}
}