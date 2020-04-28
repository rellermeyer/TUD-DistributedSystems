package chubby.replica.raft

import akka.actor.{ActorSystem, Cancellable}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

case class RaftServerState(
    ipAddress: String,
    system: ActorSystem,
    context: ActorContext[ProtocolRaft.Command],
    leader: Option[String],
    electionTerm: Int,
    allActorRefRaftServerOther: Map[String, ActorRef[ProtocolRaft.Command]],
    countVoteLeader: Int,
    lockState: LockState,
    scheduledTimeout: Cancellable,
    logState: LogState
)
