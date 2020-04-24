package chubby.replica.raft.behavior

import java.net.InetAddress

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, Behavior}
import chubby.replica.raft.ProtocolRaft._
import chubby.replica.raft.{LogCommandLock, LogEntry, ProtocolRaft, RaftServerState}

import scala.concurrent.Await
import scala.concurrent.duration._

abstract class BehaviorRaftServer {
  def apply(
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    Behaviors.receiveMessage[ProtocolRaft.Command](message => {
      val logger = raftServerState.context.system.log
      logger.info(s"Received message $message in election term ${raftServerState.electionTerm}")

      message match {
        case clientRequest: ClientRequest => processClientRequest(clientRequest, raftServerState)
        case raftRequest: RaftRequest     => processRaftRequest(raftRequest, raftServerState)
        case raftResponse: RaftResponse   => processRaftResponse(raftResponse, raftServerState)
        case TimeOut                      => processTimeOut(raftServerState)
        case announce: RaftServerAnnounce => processAnnounce(announce, raftServerState)
        case StartServer                  => Behaviors.same
      }
    })
  }

  protected def processClientRequest(
      clientRequest: ClientRequest,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = clientRequest match {
    case clientRequestLeader: ClientRequestLeader => processClientRequestLeader(clientRequestLeader, raftServerState)
    case clientRequestLock: ClientRequestLock     => processClientRequestLock(clientRequestLock, raftServerState)
  }

  protected def processRaftRequest(
      raftRequest: RaftRequest,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = raftRequest match {
    case raftRequestVoteLeader: RaftRequestVoteLeader =>
      processRaftRequestVoteLeader(raftRequestVoteLeader, raftServerState)
    case raftRequestPing: RaftRequestPing => processRaftRequestPing(raftRequestPing, raftServerState)
  }

  protected def processRaftResponse(
      raftResponse: RaftResponse,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = raftResponse match {
    case raftResponseVoteLeader: RaftResponseVoteLeader =>
      processRaftResponseVoteLeader(raftResponseVoteLeader, raftServerState)
    case raftResponsePing: RaftResponsePing => processRaftResponsePing(raftResponsePing, raftServerState)
  }

  protected def processClientRequestLeader(
      clientRequestLeader: ClientRequestLeader,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def processClientRequestLock(
      clientRequestLock: ClientRequestLock,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def processRaftResponseVoteLeader(
      raftResponseVoteLeader: RaftResponseVoteLeader,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def processRaftResponsePing(
      raftResponsePing: RaftResponsePing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def processRaftRequestVoteLeader(
      raftRequestVoteLeader: RaftRequestVoteLeader,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def processRaftRequestPing(
      raftRequestPing: ProtocolRaft.RaftRequestPing,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def processTimeOut(raftServerState: RaftServerState): Behavior[ProtocolRaft.Command]

  def determineDurationTimeOut(): FiniteDuration

  protected def processAnnounce(
      raftServerAnnounce: RaftServerAnnounce,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command]

  protected def resetScheduledTimeOut(raftServerState: RaftServerState, delay: FiniteDuration): Cancellable = {
    raftServerState.scheduledTimeout.cancel()

    raftServerState.context.scheduleOnce(delay, raftServerState.context.self, TimeOut)
  }

  def createNewBehaviorCandidate(raftServerState: RaftServerState): Behavior[ProtocolRaft.Command] = {
    val electionTermNew = raftServerState.electionTerm + 1

    val logger = raftServerState.context.system.log
    logger.info(s"Became candidate with new election term: $electionTermNew")

    raftServerState.allActorRefRaftServerOther.values.foreach(actorRef => {
      actorRef.tell(RaftRequestVoteLeader(electionTermNew, raftServerState.ipAddress, -1, -1))
    })

    BehaviorCandidate(raftServerState.copy(leader = None, countVoteLeader = 1, electionTerm = electionTermNew))
  }

  def createNewBehaviorLeader(raftServerState: RaftServerState): Behavior[ProtocolRaft.Command] = {
    val logger = raftServerState.context.system.log
    val sizeAllLog = raftServerState.logState.allLog.size
    val commitIndex = raftServerState.logState.commitIndex
    val matchIndex = raftServerState.logState.matchIndex
    logger.info(
      s"Became leader with election term: ${raftServerState.electionTerm} and logEntries: $sizeAllLog and commitIndex: $commitIndex and matchIndex: $matchIndex"
    )

    BehaviorLeader(
      raftServerState.copy(
        leader = Some(raftServerState.ipAddress),
        countVoteLeader = 0,
        scheduledTimeout = resetScheduledTimeOut(raftServerState, BehaviorLeader.determineDurationTimeOut())
      )
    )
  }

  def createNewBehaviorFollower(
      leader: String,
      electionTerm: Int,
      raftServerState: RaftServerState
  ): Behavior[ProtocolRaft.Command] = {
    val logger = raftServerState.context.system.log
    val sizeAllLog = raftServerState.logState.allLog.size
    val commitIndex = raftServerState.logState.commitIndex
    logger.info(
      s"Became follower of ${leader} with election term: $electionTerm and logEntries: $sizeAllLog and commitIndex: $commitIndex"
    )

    BehaviorFollower(
      raftServerState.copy(
        leader = Some(leader),
        electionTerm = electionTerm,
        countVoteLeader = 0,
        scheduledTimeout = resetScheduledTimeOut(raftServerState, BehaviorFollower.determineDurationTimeOut())
      )
    )
  }

  protected def updateAllActorRefRaftServerOther(
      raftServerAnnounce: RaftServerAnnounce,
      raftServerState: RaftServerState
  ): Map[String, ActorRef[ProtocolRaft.Command]] = {
    raftServerState.allActorRefRaftServerOther.updatedWith(raftServerAnnounce.ipAddress) { _ =>
      Some(
        actorRefAdapter(
          Await.result(
            raftServerState.system
              .actorSelection(s"akka.tcp://server@${raftServerAnnounce.ipAddress}:2552/user/RaftActor")
              .resolveOne(3.seconds),
            3.seconds
          )
        )
      )
    }
  }

  protected def getActorRef(ipAddress: String, raftServerState: RaftServerState): ActorRef[ProtocolRaft.Command] = {
    raftServerState.allActorRefRaftServerOther.get(ipAddress) match {
      case Some(actorRef) => actorRef
      case None           => throw new Exception(s"No ActorRef found for IP address: $ipAddress")
    }
  }
}
