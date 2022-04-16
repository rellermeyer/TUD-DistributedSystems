package de.maxbundscherer.scala.raft.services

import akka.pattern.ask
import akka.actor.{ActorRef, ActorSystem}
import akka.util.Timeout
import de.maxbundscherer.scala.raft.actors.BRaftNodeActor.BRaftNodeState
import de.maxbundscherer.scala.raft.aggregates.Aggregate.{ActualData, GetActualData, GetState, IamNotTheLeader, IamTheLeader, InitiateLeaderElection, LeaderIsSimulatingCrash, SimulateLeaderCrash, WhoIsLeader}
import de.maxbundscherer.scala.raft.schnorr.Schnorr.{generateKeypair, string_sign}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Await

// Messages from client should be signed (Implement in RaftService)
class BRaftService(numberNodes: Int)(implicit actorSystem: ActorSystem,
                                     timeout: Timeout,
                                     executionContext: ExecutionContext) {

  import de.maxbundscherer.scala.raft.actors.BRaftNodeActor
  import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate._
  // Protected to give access in tests
  private val (privateKey, publicKey): (BigInt, BigInt) = generateKeypair()

  /**
    * Declare and start up nodes
    */
  final val nodes: Map[Int, ActorRef] =
    (0 until numberNodes)
      .map(i => {
        i -> actorSystem.actorOf(props = BRaftNodeActor.props,
                                 name = s"${BRaftNodeActor.prefix}-$i")
      })
      .toMap

  /**
    * Init nodes with neighbors and keypair
    */
  var keys: Map[(Int, String), (BigInt, BigInt)] = nodes.map(node => ((node._1, node._2.path.name), generateKeypair()))

  var pKeys : Map[String, BigInt] = keys.map(x => (x._1._2, x._2._2))

  nodes.toList.sortBy(entry => entry._1).zip(keys.toList.sortBy(entry => entry._1._1)).foreach(pair => {
    val node = pair._1
    val keypair = pair._2
    print(s"creating node $node with keypair $keypair")
    node._2 ! InitActor(nodes.filter(_._1 != node._1).values.toVector, keypair._2, this.publicKey, pKeys)
  })

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
    * Return current leader (or first leader if there are multiple...?)
    * @return
    */
  def getLeader: (Int, ActorRef) = {
    nodes.filter(node => {
      Await.result(node._2 ? WhoIsLeader, timeout.duration).isInstanceOf[IamTheLeader]
    }).head
  }

  /**
    * Get all the actors in the system.
    * @return Vector with all actors in the system
    */
  def getNodeStates: Map[Int, BRaftNodeState] = {

    var nodeStates: Map[Int, BRaftNodeState] = Map.empty

    nodes.foreach(node => {

      val awaitedResult  = Await.result(node._2 ? GetState, timeout.duration)

      awaitedResult match {
        case MyStateIs(state)    => nodeStates += (node._1 -> state)
        case _ => //Ignore everything else
      }
    })

    nodeStates
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
    * Send SimulateLeaderCrash to each node (Leader is confirming - Waiting for result - blocking)
    * @return Vector with Either [Left = LeaderIsSimulatingCrash, Right = IamNotTheLeader]
    */
  def simulateByzantineActorBecomingLeader(actor: ActorRef): Unit = {
    actor ! InitiateLeaderElection
  }


  def appendData(key: String, value: String): Vector[Either[WriteResponse, IamNotTheLeader]] = {
    this.appendData(key, value, this.privateKey)
  }

  /**
   * Append data (only leader is allowed to write data - synchronized by heartbeat from leader with followers - blocking)
   * @param key String
   * @param value String
   * @return Vector with Either [Left = WriteSuccess, Right = IamNotTheLeader]
   */
  def appendData(key: String, value: String, ownPrivateKey: BigInt): Vector[Either[WriteResponse, IamNotTheLeader]] = {
    val signature = string_sign(ownPrivateKey, s"$key,$value")

    nodes.map(node => {
      val awaitedResult = Await.result(node._2 ? AppendData(key = key, value = value, signature=signature), timeout.duration)
      awaitedResult match {
        case msg: WriteResponse            => {
          if (!msg.success) {
            println(s"Appenddata unsuccesful in leader, reason: ${msg.reason}")
          }
          Left(msg)
        }
        case msg: IamNotTheLeader         => Right(msg)
        // case IamNotConsistent             => null // TODO: do not return null
      }
    }).toVector
  }

  def appendBRaftData(key: String, value: String): Unit = {
    val currentLeader = getLeader

    currentLeader._2 ! appendData(key, value)
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
