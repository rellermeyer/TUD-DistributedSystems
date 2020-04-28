package chubby.replica.raft

import akka.actor.typed.ActorRef
import chubby.common.{Lock, LockDefinition}
import chubby.grpc.{LeaderResponse, LockRequestResponse}

object ProtocolRaft {

  sealed trait Command

  sealed trait ClientRequest extends Command
  final case class ClientRequestLeader(replyTo: ActorRef[LeaderResponse]) extends ClientRequest
  final case class ClientRequestLock(replyTo: ActorRef[LockRequestResponse], lockDefinition: LockDefinition)
      extends ClientRequest

  final case object TimeOut extends Command

  sealed trait RaftRequest extends Command {
    val electionTerm: Int
    val replyTo: String
  }
  final case class RaftRequestVoteLeader(
      electionTerm: Int,
      replyTo: String,
      lastLogIndex: Int,
      lastLogTerm: Int
  ) extends RaftRequest
  final case class RaftRequestPing(
      electionTerm: Int,
      replyTo: String,
      prevLogIndex: Int,
      prevLogElectionTerm: Int,
      logEntryAppend: Option[LogEntry],
      leaderCommit: Int
  ) extends RaftRequest

  sealed trait RaftResponse extends Command {
    val electionTerm: Int
    val responseBy: String
  }

  final case class RaftResponseVoteLeader(electionTerm: Int, responseBy: String, voteGranted: Boolean)
      extends RaftResponse
  final case class RaftResponsePing(
      electionTerm: Int,
      responseBy: String,
      pingSuccess: Boolean,
      matchIndex: Int
  ) extends RaftResponse

  final case class RaftServerAnnounce(ipAddress: String) extends Command
  final case object StartServer extends Command

}
