package chubby.replica.raft.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import chubby.grpc.LeaderResponse
import chubby.replica.raft.ProtocolRaft._
import chubby.replica.raft.{ProtocolRaft, RaftServerState}

import scala.concurrent.duration._
import scala.math.min
import scala.util.Random

object BehaviorFollower extends BehaviorRaftServer {

  override protected def processClientRequestLeader(
      clientRequestLeader: ClientRequestLeader,
      raftServerState: RaftServerState
  ): Behavior[Command] = {
    raftServerState.leader match {
      case Some(leader) =>
        clientRequestLeader.replyTo.tell(
          LeaderResponse(isLeaderElected = true, isLeader = false, leader)
        )

        Behaviors.same
      case None =>
        clientRequestLeader.replyTo.tell(LeaderResponse(isLeaderElected = false, isLeader = false))

        Behaviors.same
    }
  }

  override protected def processClientRequestLock(
      clientRequestLock: ClientRequestLock,
      raftServerState: RaftServerState
  ): Behavior[Command] = ???
  // TODO: Followers can't process requests from clients.

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
      processRaftRequestPingLogAppend(raftRequestPing, raftServerState)
    } else {
      getActorRef(raftRequestPing.replyTo, raftServerState).tell(
        RaftResponsePing(raftRequestPing.electionTerm, raftServerState.ipAddress, pingSuccess = false, 0)
      )

      Behaviors.same
    }
  }

  private def processRaftRequestPingLogAppend(
      raftRequestPing: RaftRequestPing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    val logState = raftServerState.logState
    val commitIndexNew = {
      if (raftRequestPing.leaderCommit > raftServerState.logState.commitIndex) {
        min(raftRequestPing.leaderCommit, raftRequestPing.prevLogIndex + 1)
      } else {
        raftServerState.logState.commitIndex
      }
    }

    if (logState.allLog.size < raftRequestPing.prevLogIndex) {
      getActorRef(raftRequestPing.replyTo, raftServerState).tell(
        RaftResponsePing(raftRequestPing.electionTerm, raftServerState.ipAddress, pingSuccess = false, 0)
      )

      createNewBehaviorFollower(
        raftRequestPing.replyTo,
        raftRequestPing.electionTerm,
        raftServerState.copy(logState = raftServerState.logState.copy(commitIndex = commitIndexNew))
      )
    } else {
      val raftServerStateNew = processRaftRequestPingLogAppendNewEntry(raftRequestPing, raftServerState)
      getActorRef(raftRequestPing.replyTo, raftServerStateNew).tell(
        RaftResponsePing(
          raftRequestPing.electionTerm,
          raftServerStateNew.ipAddress,
          pingSuccess = true,
          raftServerStateNew.logState.allLog.size - 1
        )
      )

      createNewBehaviorFollower(
        raftRequestPing.replyTo,
        raftRequestPing.electionTerm,
        raftServerStateNew.copy(logState = raftServerStateNew.logState.copy(commitIndex = commitIndexNew))
      )
    }
  }

  private def processRaftRequestPingLogAppendNewEntry(
      raftRequestPing: RaftRequestPing,
      raftServerState: RaftServerState
  ): RaftServerState = {
    val logState = raftServerState.logState
    val logIndexEntry = raftRequestPing.prevLogIndex + 1

    if (logState.allLog.size > logIndexEntry) {
      raftRequestPing.logEntryAppend match {
        case Some(entry) =>
          if (entry.electionTerm.equals(logState.allLog(logIndexEntry).electionTerm)) {
            raftServerState
          } else {
            raftServerState.copy(logState =
              logState.copy(allLog = logState.allLog.take(raftRequestPing.prevLogIndex) :+ entry)
            )
          }
        case None =>
          raftServerState.copy(logState = logState.copy(allLog = logState.allLog.take(raftRequestPing.prevLogIndex)))
      }
    } else {
      raftServerState.copy(logState = logState.copy(allLog = logState.allLog ++ raftRequestPing.logEntryAppend.toList))
    }
  }

  override protected def processRaftResponseVoteLeader(
      raftResponseVoteLeader: RaftResponseVoteLeader,
      raftServerState: RaftServerState
  ): Behavior[Command] = Behaviors.same

  override protected def processRaftResponsePing(
      raftResponsePing: RaftResponsePing,
      raftServerState: RaftServerState
  ): Behavior[Command] = Behaviors.same

  override protected def processTimeOut(raftServerState: RaftServerState): Behavior[ProtocolRaft.Command] = {
    createNewBehaviorCandidate(raftServerState)
  }

  override def determineDurationTimeOut(): FiniteDuration = {
    val randomGenerator = Random
    val delay = 300
    val randomDelayMaximum = 300

    (delay + randomGenerator.nextInt(randomDelayMaximum)).milliseconds
  }

  override protected def processAnnounce(
      raftServerAnnounce: RaftServerAnnounce,
      raftServerState: RaftServerState
  ): Behavior[Command] = {
    val allActorRefRaftServerOtherNew = updateAllActorRefRaftServerOther(raftServerAnnounce, raftServerState)

    BehaviorFollower(raftServerState.copy(allActorRefRaftServerOther = allActorRefRaftServerOtherNew))
  }
}
