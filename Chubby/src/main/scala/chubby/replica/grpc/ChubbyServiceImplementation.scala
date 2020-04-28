package chubby.replica.grpc

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.{ActorSystem, Scheduler}
import akka.stream.Materializer
import akka.util.Timeout
import chubby.common.LockDefinition
import chubby.grpc._
import chubby.replica.raft.ProtocolRaft

import scala.concurrent.Future
import scala.concurrent.duration._

class ChubbyServiceImplementation(
    private val raftActor: ActorRef[ProtocolRaft.Command],
    implicit val sys: ActorSystem,
    implicit val mat: Materializer
) extends ChubbyService {
  implicit val timeout: Timeout = 10.seconds
  implicit val scheduler: Scheduler = sys.toTyped.scheduler

  override def requestLock(in: LockRequest): Future[LockRequestResponse] = {
    println(
      s"Received the request for lock '${in.lockIdentifier}' from '${in.clientIdentifier}' with type '${in.write}'"
    )

    raftActor.ask((ref: ActorRef[LockRequestResponse]) =>
      ProtocolRaft.ClientRequestLock(ref, LockDefinition(in.clientIdentifier, in.lockIdentifier, in.write))
    )
  }

  override def requestLeader(in: LeaderRequest): Future[LeaderResponse] = {
    println(s"Received the request to respond with the leader from '${in.clientIdentifier}'")

    raftActor.ask((ref: ActorRef[LeaderResponse]) => ProtocolRaft.ClientRequestLeader(ref))
  }
}
