package chubby.replica.raft.exception

final case class InvalidLeaderException(private val message: String = "") extends Exception(message)
