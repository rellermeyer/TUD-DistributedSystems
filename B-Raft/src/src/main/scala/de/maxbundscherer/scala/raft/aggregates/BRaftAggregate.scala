package de.maxbundscherer.scala.raft.aggregates

import de.maxbundscherer.scala.raft.actors.BRaftNodeActor.BRaftNodeState
import de.maxbundscherer.scala.raft.aggregates.Aggregate.{Request, Response}
import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate.GrantVote.GrantVoteSigned
import de.maxbundscherer.scala.raft.schnorr.Schnorr.string_sign

import scala.collection.mutable.ArrayBuffer

object BRaftAggregate {
  import akka.actor.ActorRef


  case class InitActor(neighbours: Vector[ActorRef], keyPair: (BigInt, BigInt), clientPublicKey: BigInt, keys: Map[String, BigInt]) extends Request

  case class RequestVote(term: Int) extends Request

  object GrantVote {
    sealed trait Response
    final case class GrantVoteSigned(signature: BigInt, granted: Boolean, vote: Vote) extends Response
  }

//  object GrantVote    extends Response
  case class RejectMessage(reason: String)
  case class  Heartbeat(lastHashCode: BigInt, publicKeysStorage: Map[String, BigInt], term: Int)                    extends Request
  case class  IamNotConsistent()          extends Response

  case class  OverrideData(data: ArrayBuffer[LogEntry], publicKeysStorage: Map[String, BigInt], term: Int)  extends Request
  case class  AppendEntriesResponse(index: Int, hash: BigInt) {
    var signature: BigInt = -1
    override def toString: String = {
      s"hash=$hash,index=$index"
    }

    def sign(privateKey: BigInt): Unit = {
      signature = string_sign(privateKey, this.toString)
    }
  }

  case class  BecomeByzantine() extends Request


  case class  AppendData(key: String,
                         value: String,
                         signature: BigInt)                     extends Request
  case class  WriteResponse(actorName: String, success: Boolean, reason: String) extends Response

  case class  BroadcastKey(actorID: String, publicKeys: BigInt) extends Request
  case class  BroadcastTerm(term: Int, voteRequestResponses: Map[String, GrantVoteSigned])                          extends Request
  case class  BroadcastVotesToClient()

  case class LogEntry(key: String, value: String, hash: BigInt, clientSignature: BigInt) {
     var committed: Boolean = false
    override def toString: String = {
      s"$key->$value, hash:$hash"
    }
  }

  case class Vote(granted: Boolean, from: String, to:String, term:Int) {
    override def toString: String = {
      s"$from $granted vote $to in $term"
    }
  }

  case class  MyStateIs(state: BRaftNodeState)                       extends Response

  val CLIENT_NAME: String = "client"
}