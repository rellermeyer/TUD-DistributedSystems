package chubby.common

case class LockAcquired(lockDefinition: LockDefinition, lockAcquiredStartTime: Long, lockAcquiredDuration: Long)
