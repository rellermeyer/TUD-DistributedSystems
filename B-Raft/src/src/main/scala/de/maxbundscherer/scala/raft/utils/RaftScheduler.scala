package de.maxbundscherer.scala.raft.utils

import akka.actor.Actor
import de.maxbundscherer.scala.raft.aggregates.Aggregate.NodeState

/**
  * RaftScheduler
  */
trait RaftScheduler extends Actor with Configuration {

  import de.maxbundscherer.scala.raft.actors.BRaftNodeActor.BRaftNodeState
  import de.maxbundscherer.scala.raft.aggregates.Aggregate.SchedulerTrigger
  import scala.concurrent.ExecutionContext
  import scala.concurrent.duration._

  /*
   * Set up by actor
   */
  val state: NodeState
  implicit val executionContext: ExecutionContext

  //Set electionTimeout randomized in [electionTimerIntervalMin, electionTimerIntervalMax]
  def electionTimeout : Int = Config.electionTimerIntervalMin * 1000 + scala.util.Random.nextInt(Config.electionTimerIntervalMax * 1000)

  //Set heartbeat to fixed interval
  private val heartbeatTimeout: Int = Config.heartbeatTimerInterval * 1000

  /**
    * Stop electionTimer
    */
  def stopElectionTimer(): Unit = {

    if (state.electionTimer.isDefined) {
      state.electionTimer.get.cancel()
      state.electionTimer = None
    }

  }

  /**
    * Start electionTimer (if already running = stop timer)
    */
  def restartElectionTimer(): Unit = {

    stopElectionTimer()

    state.electionTimer = Some(
      context.system.scheduler.scheduleWithFixedDelay(
        initialDelay = electionTimeout.millis,
        delay = electionTimeout.millis,
        receiver = self,
        message = SchedulerTrigger.ElectionTimeout
      ))

  }

  /**
    * Stop heartbeatTimer
    */
  def stopHeartbeatTimer(): Unit = {

    if (state.heartbeatTimer.isDefined) {
      state.heartbeatTimer.get.cancel()
      state.heartbeatTimer = None
    }

  }

  /**
    * Start heartbeatTimer (if already running = stop timer)
    */
  def restartHeartbeatTimer(): Unit = {

    stopHeartbeatTimer()

    state.heartbeatTimer = Some(
      context.system.scheduler.scheduleWithFixedDelay(
        initialDelay = heartbeatTimeout.millis,
        delay = heartbeatTimeout.millis,
        receiver = self,
        message = SchedulerTrigger.Heartbeat
      ))
  }

  /**
    * Start heartbeatTimer (if already running = stop timer)
    */
  def scheduleAwake(): Unit =
    context.system.scheduler.scheduleOnce(delay = Config.sleepDowntime.seconds,
                                          receiver = self,
                                          message = SchedulerTrigger.Awake)

}
