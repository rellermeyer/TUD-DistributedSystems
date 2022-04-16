package de.maxbundscherer.scala.raft.aggregates

import akka.actor.Cancellable


object Aggregate {
  import akka.actor.ActorRef

  trait Request
  trait Response

  /**
    * Internal (mutable) actor state
    * @param neighbours Vector with another actors
    * @param electionTimer Cancellable for timer (used in FOLLOWER and CANDIDATE behavior)
    * @param heartbeatTimer Cancellable for timer (used in LEADER behavior)
    * @param alreadyVoted Boolean (has already voted in FOLLOWER behavior)
    * @param voteCounter Int (counter in CANDIDATE behavior)
    * @param majority Int (calculated majority - set up in init)
    * @param heartbeatCounter Int (auto simulate crash after some heartbeats in LEADER behavior)
    *  @param data Map (String->String) (used in FOLLOWER and LEADER behavior)
    */
  class NodeState(
                        var neighbours            : Vector[ActorRef]    = Vector.empty,
                        var electionTimer         : Option[Cancellable] = None,
                        var heartbeatTimer        : Option[Cancellable] = None,
                        var alreadyVoted          : Boolean             = false,
                        var voteCounter           : Int                 = 0,
                        var majority              : Int                 = -1,
                        var heartbeatCounter      : Int                 = 0,
                        var data                  : Map[String, String] = Map.empty,
                      )

  case class  GetActualData(data: Map[String, String])          extends Request
  case class  ActualData(data: Map[String, String])             extends Response

  object      WhoIsLeader                                       extends Request
  case class  IamTheLeader(actorName: String)                   extends Response
  case class  IamNotTheLeader(actorName: String)                extends Response

  object      GetState                                          extends Request

  object      SimulateLeaderCrash                               extends Request
  case class  LeaderIsSimulatingCrash(actorName: String)        extends Response

  object      InitiateLeaderElection
    extends Response

  //FSM States (RaftNodeActor)
  object BehaviorEnum extends Enumeration {
    type BehaviorEnum = Value
    val UNINITIALIZED, FOLLOWER, CANDIDATE, LEADER, SLEEP, BYZANTINELEADER = Value
  }

  //Used by RaftScheduler
  object SchedulerTrigger {
    object ElectionTimeout
    object Heartbeat
    object Awake
  }
}
