package de.maxbundscherer.scala.raft.services

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import de.maxbundscherer.scala.raft.aggregates.Aggregate.{ActualData, GetActualData, IamNotTheLeader, IamTheLeader, LeaderIsSimulatingCrash, SimulateLeaderCrash, WhoIsLeader}

import scala.concurrent.{Await, ExecutionContext, Future}

class RaftService(numberNodes: Int)(implicit actorSystem: ActorSystem,
                                    timeout: Timeout,
                                    executionContext: ExecutionContext) {

  import de.maxbundscherer.scala.raft.actors.RaftNodeActor
  import de.maxbundscherer.scala.raft.aggregates.RaftAggregate._

  /**
    * Declare and start up nodes
    */
  final val nodes: Map[Int, ActorRef] =
    (0 until numberNodes)
      .map(i => {
        i -> actorSystem.actorOf(props = RaftNodeActor.props,
                                 name = s"${RaftNodeActor.prefix}-$i")
      })
      .toMap

  /**
    * Init nodes (each node with neighbors)
    */
  nodes.foreach(node =>
    node._2 ! InitActor(nodes.filter(_._1 != node._1).values.toVector))

  /**
   * Ask each node: Are you the leader? (Waiting for result - blocking)
   * @return Vector with Either [Left = IamTheLeader, Right = IamNotTheLeader]
   */
  def evaluateActualLeaders: Vector[Either[IamTheLeader, IamNotTheLeader]] = {

    nodes.map(node => {

      val awaitedResult = Await.result(node._2 ? WhoIsLeader, timeout.duration)

      awaitedResult match {
        case msg: IamTheLeader    => Left(msg)
        case msg: IamNotTheLeader => Right(msg)
      }

    }).toVector

  }

  /**
   * Send SimulateLeaderCrash to each node (Leader is confirming - Waiting for result - blocking)
   * @return Vector with Either [Left = LeaderIsSimulatingCrash, Right = IamNotTheLeader]
   */
  def simulateLeaderCrash(): Vector[Either[LeaderIsSimulatingCrash, IamNotTheLeader]] = {

    nodes.map(node => {

      val awaitedResult = Await.result(node._2 ? SimulateLeaderCrash, timeout.duration)

      awaitedResult match {
        case msg: LeaderIsSimulatingCrash => Left(msg)
        case msg: IamNotTheLeader         => Right(msg)
      }

    }).toVector

  }

  /**
   * Append data (only leader is allowed to write data - synchronized by heartbeat from leader with followers - blocking)
   * @param key String
   * @param value String
   * @return Vector with Either [Left = WriteSuccess, Right = IamNotTheLeader]
   */
  def appendData(key: String, value: String): Vector[Either[WriteSuccess, IamNotTheLeader]] = {

    nodes.map(node => {

      val awaitedResult = Await.result(node._2 ? AppendData(key = key, value = value), timeout.duration)

      awaitedResult match {
        case msg: WriteSuccess            => Left(msg)
        case msg: IamNotTheLeader         => Right(msg)
      }

    }).toVector

  }

  /**
   * Ask each node: Provide your actual data (blocking)
   * @return Vector with ActualData
   */
  def evaluateActualData: Vector[ActualData] = {

    nodes.map(node => {

      Await.result(node._2 ? GetActualData, timeout.duration).asInstanceOf[ActualData]

    }).toVector

  }

  /**
   * Terminates actor system
   */
  def terminate(): Future[Boolean] = actorSystem.terminate().map(_ => true)

}
