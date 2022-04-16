package de.maxbundscherer.scala.raft.BRaft

import de.maxbundscherer.scala.raft.BRaft.utils.BaseServiceTest
import de.maxbundscherer.scala.raft.actors.BRaftNodeActor.BRaftNodeState
import de.maxbundscherer.scala.raft.utils.Configuration

class BroadcastTest extends BaseServiceTest with Configuration {

  "Broadcast" should {

    "terms after after one election should be the same" in {

      Thread.sleep(3000)

      val nodeStates : Map[Int, BRaftNodeState] = raftService.getNodeStates

      val terms : Iterable[Int] = nodeStates.map(x => x._2.term)

      terms.forall(_ == terms.head) shouldBe(true)
    }

//    "elect new leader after leader crash" in {
//
//      Thread.sleep(3000)
//
//
//      val data: Vector[Either[IamTheLeader, IamNotTheLeader]] = raftService.evaluateActualLeaders
//
//      val localLeaderName = data.filter(_.isLeft).head match {
//        case Left(left) => left.actorName
//        case _ => ""
//      }
//
//      data.count(_.isLeft)  shouldBe 1 //Only on leader
//      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
//
//      val nodeStates : Map[Int, NodeState] = raftService.getNodeStates
//
//      val terms : Iterable[Int] = nodeStates.map(x => x._2.term)
//
//      terms.toList.distinct.length shouldBe(1)
//    }

    "terminate actor system" in {

      raftService.terminate().map(response => response shouldBe true)
    }
  }
}