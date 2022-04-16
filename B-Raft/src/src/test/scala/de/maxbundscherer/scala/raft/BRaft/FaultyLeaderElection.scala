package de.maxbundscherer.scala.raft.BRaft

import akka.actor.ActorRef
import de.maxbundscherer.scala.raft.BRaft.utils.BaseServiceTest
import de.maxbundscherer.scala.raft.actors.BRaftNodeActor.BRaftNodeState
import de.maxbundscherer.scala.raft.aggregates.Aggregate._
import de.maxbundscherer.scala.raft.utils.Configuration

class FaultyLeaderElection extends BaseServiceTest with Configuration {

  "Election" should {

    var temporaryFirstLeaderName: String    = ""

    "elect only one leader" in {

      freezeTest(seconds = Config.nodes * Config.electionTimerIntervalMax, loggerMessage = "Waiting for first leader election")

      val data: Vector[Either[IamTheLeader, IamNotTheLeader]] = raftService.evaluateActualLeaders

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      temporaryFirstLeaderName = localLeaderName

      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
    }

    "has node become candidate" in {

      val nodeStates : Map[Int, BRaftNodeState] = raftService.getNodeStates

      // Every node except the leader
      val nodeFollowerStates = nodeStates.filter(x => x._2.behaviour != BehaviorEnum.LEADER)

      val (nodeActorID, _) : (Int, BRaftNodeState) = nodeFollowerStates.head

      val actor : Option[ActorRef] = raftService.nodes.get(nodeActorID)

      actor match {
        case Some(actor) => actor ! InitiateLeaderElection
      }


      val nodeStates_after : Map[Int, BRaftNodeState] = raftService.getNodeStates

      val state = nodeStates_after.get(nodeActorID)

      state match {
        case Some(state) => state.behaviour.shouldBe(BehaviorEnum.CANDIDATE)
      }
    }

    "simulate leader crash" in {

      val data: Vector[Either[LeaderIsSimulatingCrash, IamNotTheLeader]] = raftService.simulateLeaderCrash()

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      localLeaderName       shouldBe temporaryFirstLeaderName
      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
    }


    "elect new leader after leader crash" in {

      freezeTest(seconds = Config.nodes * Config.electionTimerIntervalMax, loggerMessage = "Waiting for second leader election")

      val data: Vector[Either[IamTheLeader, IamNotTheLeader]] = raftService.evaluateActualLeaders

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      localLeaderName       should not be temporaryFirstLeaderName
      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
    }

    "terminate actor system" in {

      raftService.terminate().map(response => response shouldBe true)
    }
  }
}
