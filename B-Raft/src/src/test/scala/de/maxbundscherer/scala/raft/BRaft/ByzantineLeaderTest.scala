package de.maxbundscherer.scala.raft.BRaft

import de.maxbundscherer.scala.raft.BRaft.utils.BaseServiceTest
import de.maxbundscherer.scala.raft.aggregates.Aggregate.{BehaviorEnum, IamNotTheLeader}
import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate.{BecomeByzantine, WriteResponse}
import de.maxbundscherer.scala.raft.utils.Configuration

class ByzantineLeaderTest extends BaseServiceTest with Configuration {
  // Test Byzantine Behavior
  freezeTest(10, "Waiting for startup")
  "start byzantine behavior" in {
    val (leaderID, leaderNodeRef) = raftService.getLeader
    leaderNodeRef ! BecomeByzantine
    Thread.sleep(3000)
    val leaderState = raftService.getNodeStates.filter(tuple => {
      tuple._1 == leaderID
    }).head._2

    val leaders = raftService.getNodeStates.filter(tuple => {
      tuple._2.behaviour == BehaviorEnum.BYZANTINELEADER || tuple._2.behaviour == BehaviorEnum.LEADER
    }).keys

    log.info(s"leaders: $leaders")
    Thread.sleep(8000)
    leaders.size shouldBe 1
    leaderState.behaviour shouldBe BehaviorEnum.BYZANTINELEADER
  }

  "byzantine leader adds entry to log by itself (not received from client)" in {
    // AppendData, but don't sign message
    val data: Vector[Either[WriteResponse, IamNotTheLeader]] = raftService.appendData("testkey", "testvalue", 999999999)
    Thread.sleep(3000)
    data.size shouldBe(5)
  }
}
