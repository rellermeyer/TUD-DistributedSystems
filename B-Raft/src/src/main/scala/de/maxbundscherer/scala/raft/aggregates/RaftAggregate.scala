package de.maxbundscherer.scala.raft.aggregates

import de.maxbundscherer.scala.raft.aggregates.Aggregate.Request
import de.maxbundscherer.scala.raft.aggregates.Aggregate.Response

object RaftAggregate {
  import akka.actor.ActorRef


  case class InitActor(neighbours: Vector[ActorRef]) extends Request

  object RequestVote  extends Request
  object GrantVote    extends Response

  case class  Heartbeat(lastHashCode: Int)            extends Request
  object      IamNotConsistent                        extends Response
  case class  OverrideData(data: Map[String, String]) extends Request

  case class  AppendData(key: String, value: String)  extends Request
  case class  WriteSuccess(actorName: String)         extends Response

}