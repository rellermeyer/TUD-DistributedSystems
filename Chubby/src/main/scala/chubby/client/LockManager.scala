package chubby.client

import chubby.common.Lock

import scala.collection.mutable

class LockManager() {
  var locks: mutable.HashMap[String, Lock] = new mutable.HashMap[String, Lock]()

  def append(lock: Lock): Any = {
    if (lock.isExpired) {
      throw new Exception(s"[${System.currentTimeMillis()}] Adding an expired lock is prohibited!")
    }
    println(s"[LOCK] Adding lock '${lock.identifier}' ('${lock.write}')")
    this.locks(lock.identifier) = lock
  }

  def release(lock: Lock): Any = {
    println(s"[LOCK] Removing lock '${lock.identifier}'")
    this.locks -= lock.identifier
  }

  def assertContainsValidLock(identifier: String, write: Boolean = false): Unit = {
    if (!this.locks.contains(identifier)) {
      throw new Exception(s"[${System.currentTimeMillis()}] the lock is not acquired")
    }

    val lock: Lock = this.locks(identifier)

    if (lock.isExpired) {
      this.release(lock)
      throw new Exception(s"[${System.currentTimeMillis()}] the lock is expired")
    }

    if (write && !lock.write) {
      throw new Exception(s"[${System.currentTimeMillis()}] the lock is read only.")
    }
  }

  def containsValidLock(identifier: String, write: Boolean = false): Boolean = {
    try {
      this.assertContainsValidLock(identifier, write)
      true
    } catch {
      case e: Throwable =>
//        println(s"LockManager does not contain valid lock '${identifier}' (${write}) because '${e.getMessage}'")
        false
    }
  }
}
