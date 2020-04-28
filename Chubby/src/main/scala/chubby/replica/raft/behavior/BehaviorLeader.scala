package chubby.replica.raft.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import chubby.common.LockDefinition
import chubby.grpc.{LeaderResponse, LockRequestResponse}
import chubby.replica.raft.ProtocolRaft._
import chubby.replica.raft._

import scala.concurrent.duration._
import scala.math.max

object BehaviorLeader extends BehaviorRaftServer {

  override protected def processClientRequestLeader(
      clientRequestLeader: ClientRequestLeader,
      raftServerState: RaftServerState
  ): Behavior[Command] = {
    clientRequestLeader.replyTo.tell(LeaderResponse(isLeaderElected = true, isLeader = true, raftServerState.ipAddress))

    Behaviors.same
  }

  override protected def processClientRequestLock(
      clientRequestLock: ClientRequestLock,
      raftServerState: RaftServerState
  ): Behavior[Command] = {
    val logger = raftServerState.context.system.log
    val identifier = clientRequestLock.lockDefinition.lockIdentifier
    logger.info(s"Appended request to log with identifier: $identifier")

    val logEntryNew = LogEntry(
      clientRequestLock.replyTo,
      LogCommandLock(clientRequestLock.lockDefinition),
      raftServerState.electionTerm
    )

    BehaviorLeader(
      raftServerState.copy(
        logState = raftServerState.logState.copy(
          allLog = raftServerState.logState.allLog :+ logEntryNew
        )
      )
    )
  }

  override protected def processRaftRequestVoteLeader(
      raftRequestVoteLeader: ProtocolRaft.RaftRequestVoteLeader,
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
    if (raftRequestPing.electionTerm > raftServerState.electionTerm) {
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
  ): Behavior[Command] = Behaviors.same

  override protected def processRaftResponsePing(
      raftResponsePing: RaftResponsePing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    if (raftResponsePing.pingSuccess) {
      processRaftResponsePingSuccess(raftResponsePing, raftServerState)
    } else {
      processRaftResponsePingFailure(raftResponsePing, raftServerState)
    }
  }

  private def processRaftResponsePingSuccess(
      raftResponsePing: RaftResponsePing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    val raftServerStateUpdatedMatchIndex =
      updateMatchIndex(raftResponsePing.responseBy, raftResponsePing.matchIndex, raftServerState)
    val raftServerStateUpdatedNextIndex =
      updateNextIndex(raftResponsePing.responseBy, raftResponsePing.matchIndex, raftServerStateUpdatedMatchIndex)
    val raftServerStateNew = commitLogEntryIfNeeded(raftServerStateUpdatedNextIndex)

    BehaviorLeader(raftServerStateNew)
  }

  private def updateMatchIndex(
      ipAddressServer: String,
      matchIndexServer: Int,
      raftServerState: RaftServerState
  ): RaftServerState = {
    raftServerState.copy(
      logState =
        raftServerState.logState.copy(matchIndex = raftServerState.logState.matchIndex.updatedWith(ipAddressServer) {
          _ => Some(matchIndexServer)
        })
    )
  }

  private def updateNextIndex(
      ipAddressServer: String,
      matchIndexServer: Int,
      raftServerState: RaftServerState
  ): RaftServerState = {
    raftServerState.copy(
      logState =
        raftServerState.logState.copy(nextIndex = raftServerState.logState.nextIndex.updatedWith(ipAddressServer) { _ =>
          Some(matchIndexServer + 1)
        })
    )
  }

  private def commitLogEntryIfNeeded(
      raftServerState: RaftServerState
  ): RaftServerState = {
    if (shouldIncreaseCommitIndex(raftServerState.logState.commitIndex, raftServerState.logState.matchIndex)) {
      val commitIndexNew = raftServerState.logState.commitIndex + 1
      val logEntry = raftServerState.logState.allLog(commitIndexNew)
      val lockDefinition = logEntry.logCommand.lockDefinition

      val raftServerStateCommitted =
        raftServerState.copy(logState = raftServerState.logState.copy(commitIndex = commitIndexNew))
      val logger = raftServerState.context.system.log

      if (canGetLock(raftServerStateCommitted.lockState, lockDefinition)) {
        logger.info(s"Gave lock to ${lockDefinition.clientIdentifier} with id ${lockDefinition.lockIdentifier}")

        logEntry.requestedBy.tell(
          LockRequestResponse(lockDefinition.clientIdentifier, lockDefinition.lockIdentifier, locked = true)
        )

        appendLogEntryToLockState(raftServerStateCommitted, commitIndexNew, lockDefinition)
      } else {
        logger.info(s"Denied lock for ${lockDefinition.clientIdentifier} with id ${lockDefinition.lockIdentifier}")
        logEntry.requestedBy.tell(
          LockRequestResponse(lockDefinition.clientIdentifier, lockDefinition.lockIdentifier, locked = false)
        )

        raftServerStateCommitted
      }

    } else {
      raftServerState
    }
  }

  private def canGetLock(lockState: LockState, lockDefinition: LockDefinition): Boolean = {
    val lockId = lockDefinition.lockIdentifier

    if (lockDefinition.isWrite) {
      canGetReadLock(lockState, lockId) && canGetWriteLock(lockState, lockId)
    } else {
      canGetWriteLock(lockState, lockId)
    }
  }

  private def canGetWriteLock(lockState: LockState, lockId: String): Boolean = {
    !lockState.allLockWrite.contains(lockId)
  }

  private def canGetReadLock(lockState: LockState, lockId: String): Boolean = {
    !lockState.allLockRead.contains(lockId)
  }

  private def appendLogEntryToLockState(
      raftServerState: RaftServerState,
      commitIndexNew: Int,
      lockDefinition: LockDefinition
  ): RaftServerState = {
    val lockState = raftServerState.lockState
    val lockStateNew = if (lockDefinition.isWrite) {
      raftServerState.lockState.copy(allLockWrite = lockState.allLockWrite :+ lockDefinition.lockIdentifier)
    } else {
      raftServerState.lockState.copy(allLockRead = lockState.allLockRead :+ lockDefinition.lockIdentifier)
    }

    raftServerState.copy(lockState = lockStateNew)
  }

  private def shouldIncreaseCommitIndex(
      currentCommitIndex: Int,
      matchIndex: Map[String, Int]
  ): Boolean = {
    matchIndex.count(entry => entry._2 > currentCommitIndex).floatValue() + 1 > (matchIndex.keySet.size / 2)
  }

  private def processRaftResponsePingFailure(
      raftResponsePing: RaftResponsePing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    val nextIndexNew = raftServerState.logState.nextIndex.updatedWith(raftResponsePing.responseBy) {
      case Some(index) => Some(max(0, index - 1))
      case None        => throw new Exception(s"Log index for ${raftResponsePing.responseBy} not found")
    }

    BehaviorLeader(
      raftServerState.copy(logState =
        raftServerState.logState.copy(
          nextIndex = nextIndexNew
        )
      )
    )
  }

  override protected def processTimeOut(raftServerState: RaftServerState): Behavior[ProtocolRaft.Command] = {
    raftServerState.allActorRefRaftServerOther.foreach(serverOther => {
      val nextIndexServerOption = raftServerState.logState.nextIndex.get(serverOther._1)

      nextIndexServerOption match {
        case Some(nextIndexServer) =>
          val logState = raftServerState.logState

          if (nextIndexServer < logState.allLog.size) {
            tellAppendEntry(serverOther._1, raftServerState, Some(logState.allLog(nextIndexServer)), nextIndexServer)
          } else {
            tellAppendEntry(serverOther._1, raftServerState, None, nextIndexServer)
          }
        case None => throw new Exception(s"Log index for $serverOther not found")
      }
    })

    createNewBehaviorLeader(raftServerState)
  }

  private def tellAppendEntry(
      serverOther: String,
      raftServerState: RaftServerState,
      logEntryAppend: Option[LogEntry],
      logIndexServer: Int
  ): Unit = {
    val logState = raftServerState.logState

    val electionTermLog = if (logIndexServer < 1) {
      0
    } else {
      logState.allLog(logIndexServer - 1).electionTerm
    }

    getActorRef(serverOther, raftServerState).tell(
      RaftRequestPing(
        raftServerState.electionTerm,
        raftServerState.ipAddress,
        logIndexServer - 1,
        electionTermLog,
        logEntryAppend,
        logState.commitIndex
      )
    )
  }

  override def determineDurationTimeOut(): FiniteDuration = 100.milliseconds

  override protected def processAnnounce(
      raftServerAnnounce: RaftServerAnnounce,
      raftServerState: RaftServerState
  ): Behavior[Command] = {
    val allActorRefRaftServerOtherNew = updateAllActorRefRaftServerOther(raftServerAnnounce, raftServerState)

    BehaviorLeader(raftServerState.copy(allActorRefRaftServerOther = allActorRefRaftServerOtherNew))
  }
}
