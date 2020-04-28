package chubby.replica.raft

case class LockState(allLockRead: List[String], allLockWrite: List[String])
