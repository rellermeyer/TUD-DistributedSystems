package chubby.common

case class LockDefinition(clientIdentifier: String, lockIdentifier: String, isWrite: Boolean)
