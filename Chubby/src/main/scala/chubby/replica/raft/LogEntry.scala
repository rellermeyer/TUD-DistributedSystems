package chubby.replica.raft

import akka.actor.typed.ActorRef
import chubby.common.LockDefinition
import chubby.grpc.LockRequestResponse

case class LogEntry(requestedBy: ActorRef[LockRequestResponse], logCommand: LogCommandLock, electionTerm: Int)

case class LogCommandLock(lockDefinition: LockDefinition)
