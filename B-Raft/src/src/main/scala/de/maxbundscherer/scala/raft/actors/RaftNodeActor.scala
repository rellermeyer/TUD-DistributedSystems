package de.maxbundscherer.scala.raft.actors

import akka.actor.{Actor, ActorLogging}
import de.maxbundscherer.scala.raft.aggregates.Aggregate.BehaviorEnum.BehaviorEnum
import de.maxbundscherer.scala.raft.aggregates.Aggregate._
import de.maxbundscherer.scala.raft.aggregates.RaftAggregate._
import de.maxbundscherer.scala.raft.utils.{Configuration, RaftScheduler}

import scala.concurrent.ExecutionContext

object RaftNodeActor {

  import akka.actor.Props

  val prefix: String  = "raftNodeActor"
  def props()(implicit executionContext: ExecutionContext): Props = Props(new RaftNodeActor())

  /**
    * Internal (mutable) actor state
    *  @param lastHashCode Int (last hashcode from data) (used in FOLLOWER and LEADER behavior)
    */
  case class RaftNodeState(
      var lastHashCode          : Int                 = -1,
  ) extends NodeState
}

/**
  * ------------------
  * --- Raft Node ----
  * ------------------
  *
  * # 5 Behaviors (Finite-state machine / FSM)
  *
  * !!! SEE PROJECT README !!!
  *
  * - (UNINITIALIZED)
  * - FOLLOWER (Default - after init)
  * - CANDIDATE (after election timeout)
  * - LEADER
  * - (SLEEP) (after simulated crash in LEADER)
  */
class RaftNodeActor()(implicit val executionContext: ExecutionContext)
    extends Actor
    with ActorLogging
    with RaftScheduler
    with Configuration {

  import RaftNodeActor._


  /**
    * Mutable actor state
    */
  override val state: RaftNodeState = RaftNodeState()

  val notHandled = new Object

  val notHandledFun: Any => Object = (_: Any) => notHandled

  override def aroundReceive(receive: Receive, msg: Any): Unit = {
    log.info(s"${System.currentTimeMillis()},Received message:${msg.getClass.toGenericString},,")
    super.aroundReceive(receive, msg)
  }

  log.info("Actor online (uninitialized)")

  /**
    * Change actor behavior
    * @param fromBehavior Behavior
    * @param toBehavior Behavior
    * @param loggerMessage String (logging)
    */
  private def changeBehavior(fromBehavior: BehaviorEnum,
                             toBehavior: BehaviorEnum,
                             loggerMessage: String): Unit = {

    log.info(s"${System.currentTimeMillis()},Change behavior from '$fromBehavior' to '$toBehavior' ($loggerMessage)")

    /**
      * Before change of behavior
      */
    val newBehavior: Receive = toBehavior match {

      case BehaviorEnum.FOLLOWER =>
        restartElectionTimer()
        stopHeartbeatTimer()
        followerBehavior

      case BehaviorEnum.CANDIDATE =>
        restartElectionTimer()
        stopHeartbeatTimer()
        candidateBehavior

      case BehaviorEnum.LEADER =>
        stopElectionTimer()
        restartHeartbeatTimer()
        leaderBehavior

      case BehaviorEnum.SLEEP =>
        stopElectionTimer()
        stopHeartbeatTimer()
        sleepBehavior

      case _ =>
        stopElectionTimer()
        stopHeartbeatTimer()
        receive

    }

    /**
      * Change of behavior
      */
    context.become(newBehavior)

    /**
      * After change of behavior
      */
    toBehavior match {

      case BehaviorEnum.FOLLOWER =>

        state.alreadyVoted = false

      case BehaviorEnum.CANDIDATE =>

        state.voteCounter = 0
        state.neighbours.foreach(neighbour => neighbour ! RequestVote)
        self ! GrantVote

      case BehaviorEnum.LEADER =>

        state.heartbeatCounter = 0

      case BehaviorEnum.SLEEP =>

        scheduleAwake()

      case _ =>

    }

  }

  /**
    * Uninitialized behavior
    */
  override def receive: Receive = {

    case InitActor(neighbours) =>

      state.neighbours = neighbours
      state.majority = ( (neighbours.size + 1) / 2 ) + 1

      changeBehavior(
        fromBehavior = BehaviorEnum.UNINITIALIZED,
        toBehavior = BehaviorEnum.FOLLOWER,
        loggerMessage = s"Got ${state.neighbours.size} neighbours (majority=${state.majority})"
      )

    case _: Any => log.error("Node is not initialized")

  }

  /**
    * Raft FOLLOWER
    */
  def followerBehavior: Receive = {

    case SchedulerTrigger.ElectionTimeout =>

      changeBehavior(fromBehavior = BehaviorEnum.FOLLOWER,
                     toBehavior = BehaviorEnum.CANDIDATE,
                     loggerMessage = "No heartbeat from leader")

    case SimulateLeaderCrash => sender ! IamNotTheLeader(actorName = self.path.name)

    case WhoIsLeader         => sender ! IamNotTheLeader(actorName = self.path.name)

    case _: AppendData       => sender ! IamNotTheLeader(actorName = self.path.name)

    case GetActualData =>

      log.info(s"Returning actual data ${state.data}")
      sender ! ActualData(data = state.data)

    case Heartbeat(lastHashCode) =>

      log.debug(s"Got heartbeat from (${sender().path.name})")

      if(! lastHashCode.equals(state.lastHashCode)) {

        log.info("I am not consistent - request data from leader")
        sender ! IamNotConsistent
      }

      restartElectionTimer()

    case OverrideData(newData) =>

      state.data = newData
      state.lastHashCode = state.data.hashCode()

      log.info(s"[FOLLOWER WRITING DATA], ${System.currentTimeMillis()}, (data = ${state.data}), (newHashCode = ${state.lastHashCode}), The follower is writing the new data")


      //log.info(s"Follower is writing data (newHashCode = ${state.lastHashCode})")

    case RequestVote =>

      if(!state.alreadyVoted) {
        sender ! GrantVote
        state.alreadyVoted = true
      }

    case any: Any =>

      log.warning(s"Got unhandled message in followerBehavior '${any.getClass.getSimpleName}' from (${sender().path.name})")

  }

  /**
    * Raft CANDIDATE
    */
  def candidateBehavior: Receive = {

    case SchedulerTrigger.ElectionTimeout =>

      changeBehavior(
        fromBehavior = BehaviorEnum.CANDIDATE,
        toBehavior = BehaviorEnum.FOLLOWER,
        loggerMessage = s"Not enough votes (${state.voteCounter}/${state.majority})")

    case _: Heartbeat =>    //Ignore message

    case RequestVote =>     //Ignore message

    case SimulateLeaderCrash => sender ! IamNotTheLeader(actorName = self.path.name)

    case WhoIsLeader         => sender ! IamNotTheLeader(actorName = self.path.name)

    case _: AppendData       => sender ! IamNotTheLeader(actorName = self.path.name)

    case GrantVote =>

      state.voteCounter = state.voteCounter + 1

      log.debug(s"Got vote ${state.voteCounter}/${state.majority} from (${sender().path.name})")

      if (state.voteCounter >= state.majority) {

        changeBehavior(
          fromBehavior = BehaviorEnum.CANDIDATE,
          toBehavior = BehaviorEnum.LEADER,
          loggerMessage = s"Become leader - enough votes (${state.voteCounter}/${state.majority})"
        )

      }

    case any: Any =>

      log.warning(s"Got unhandled message in candidateBehavior '${any.getClass.getSimpleName}' from (${sender().path.name})")

  }

  /**
    * Raft LEADER
    */
  def leaderBehavior: Receive = {

    case SchedulerTrigger.Heartbeat =>

      state.neighbours.foreach(neighbour => neighbour ! Heartbeat(lastHashCode = state.lastHashCode))

      state.heartbeatCounter = state.heartbeatCounter + 1

      if (state.heartbeatCounter >= Config.crashIntervalHeartbeats) {
        changeBehavior(
          fromBehavior = BehaviorEnum.LEADER,
          toBehavior = BehaviorEnum.SLEEP,
          loggerMessage =  s"Simulated test crash (crashIntervalHeartbeats) - sleep ${Config.sleepDowntime} seconds now"
        )
      }

    case GrantVote =>   //Ignore message

    case RequestVote => //Ignore message

    case SimulateLeaderCrash =>

      sender ! LeaderIsSimulatingCrash(actorName = self.path.name)

      changeBehavior(
        fromBehavior = BehaviorEnum.LEADER,
        toBehavior = BehaviorEnum.SLEEP,
        loggerMessage =  s"Simulated test crash (externalTrigger) - sleep ${Config.sleepDowntime} seconds now"
      )

    case WhoIsLeader =>

      sender ! IamTheLeader(actorName = self.path.name)

    case AppendData(key, value) =>

      state.data = state.data + (key -> value)
      state.lastHashCode = state.data.hashCode()

      log.info(s"[SEND APR], ${System.currentTimeMillis()}, (key/value = $key->$value), (newHashCode = ${state.lastHashCode}), Leader is writing data")

      sender ! WriteSuccess(actorName = self.path.name)

    case GetActualData =>
      log.info(s"Returning actual data ${state.data}")
      sender ! ActualData(data = state.data)

    case IamNotConsistent =>

      sender ! OverrideData(data = state.data)

    case any: Any =>

      log.warning(s"Got unhandled message in leaderBehavior '${any.getClass.getSimpleName}' from (${sender().path.name})")

  }

  /**
    * Sleep behavior
    */
  def sleepBehavior: Receive = {

    case SchedulerTrigger.Awake =>

      changeBehavior(fromBehavior = BehaviorEnum.SLEEP,
                     toBehavior = BehaviorEnum.FOLLOWER,
                     loggerMessage = s"Awake after ${Config.sleepDowntime} seconds downtime")

    case SimulateLeaderCrash => sender ! IamNotTheLeader(actorName = self.path.name)

    case WhoIsLeader         => sender ! IamNotTheLeader(actorName = self.path.name)

    case _: AppendData       => sender ! IamNotTheLeader(actorName = self.path.name)

  }

}