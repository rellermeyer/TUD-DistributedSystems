package chubby.replica.raft.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import chubby.grpc.LeaderResponse
import chubby.replica.raft.ProtocolRaft.{
  RaftRequestPing,
  RaftRequestVoteLeader,
  RaftResponsePing,
  RaftResponseVoteLeader
}
import chubby.replica.raft.{ProtocolRaft, RaftServerState}

import scala.concurrent.duration._
import scala.util.Random

object BehaviorCandidate extends BehaviorRaftServer {

  override protected def processClientRequestLeader(
      clientRequestLeader: ProtocolRaft.ClientRequestLeader,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    clientRequestLeader.replyTo.tell(LeaderResponse(isLeaderElected = false, isLeader = false))

    Behaviors.same
  }

  override protected def processClientRequestLock(
      clientRequestLock: ProtocolRaft.ClientRequestLock,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = ???
  // TODO: Candidates can't process requests from clients.

  override protected def processRaftRequestVoteLeader(
      raftRequestVoteLeader: RaftRequestVoteLeader,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    if (raftRequestVoteLeader.electionTerm > raftServerState.electionTerm) {
      getActorRef(raftRequestVoteLeader.replyTo, raftServerState).tell(
        RaftResponseVoteLeader(raftRequestVoteLeader.electionTerm, raftServerState.ipAddress, voteGranted = true)
      )

      createNewBehaviorFollower(raftRequestVoteLeader.replyTo, raftRequestVoteLeader.electionTerm, raftServerState)
    } else {
      getActorRef(raftRequestVoteLeader.replyTo, raftServerState).tell(
        RaftResponseVoteLeader(raftRequestVoteLeader.electionTerm, raftServerState.ipAddress, voteGranted = false)
      )

      Behaviors.same
    }
  }

  override protected def processRaftRequestPing(
      raftRequestPing: RaftRequestPing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    if (raftRequestPing.electionTerm >= raftServerState.electionTerm) {
      getActorRef(raftRequestPing.replyTo, raftServerState).tell(
        RaftResponsePing(
          raftRequestPing.electionTerm,
          raftServerState.ipAddress,
          pingSuccess = true,
          raftServerState.logState.allLog.size - 1
        )
      )

      createNewBehaviorFollower(raftRequestPing.replyTo, raftRequestPing.electionTerm, raftServerState)
    } else {
      getActorRef(raftRequestPing.replyTo, raftServerState).tell(
        RaftResponsePing(raftRequestPing.electionTerm, raftServerState.ipAddress, pingSuccess = false, 0)
      )

      Behaviors.same
    }
  }

  override protected def processRaftResponseVoteLeader(
      raftResponseVoteLeader: RaftResponseVoteLeader,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    if (raftResponseVoteLeader.voteGranted) {
      if (raftServerState.countVoteLeader + 1 > (raftServerState.allActorRefRaftServerOther.size + 1).toFloat / 2) {
        createNewBehaviorLeader(raftServerState)
      } else {
        BehaviorCandidate(raftServerState.copy(countVoteLeader = raftServerState.countVoteLeader + 1))
      }
    } else {
      Behaviors.same
    }
  }

  override protected def processRaftResponsePing(
      raftResponsePing: RaftResponsePing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    Behaviors.same
  }

  override protected def processTimeOut(raftServerState: RaftServerState): Behavior[ProtocolRaft.Command] = {
    createNewBehaviorCandidate(raftServerState)
  }

  override def determineDurationTimeOut(): FiniteDuration = {
    val randomGenerator = Random
    val delay = 500
    val randomDelayMaximum = 500

    (delay + randomGenerator.nextInt(randomDelayMaximum)).milliseconds
  }

  override protected def processAnnounce(
      raftServerAnnounce: ProtocolRaft.RaftServerAnnounce,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    val allActorRefRaftServerOtherNew = updateAllActorRefRaftServerOther(raftServerAnnounce, raftServerState)

    BehaviorCandidate(raftServerState.copy(allActorRefRaftServerOther = allActorRefRaftServerOtherNew))
  }
}
