package de.maxbundscherer.scala.raft.BRaft

import de.maxbundscherer.scala.raft.BRaft.utils.BaseServiceTest
import de.maxbundscherer.scala.raft.aggregates.Aggregate._
import de.maxbundscherer.scala.raft.utils.Configuration

class BRaftServiceTest extends BaseServiceTest with Configuration {

  import de.maxbundscherer.scala.raft.aggregates.BRaftAggregate._

  "RaftService" should {

    var temporaryFirstLeaderName: String    = ""
    var temporaryData: Map[String, String]  = Map.empty
    var lastHash: BigInt = -1

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

    "append data in leader node" in {
      println("append data in leader node")
      val newDataKey    = "key1"
      val newDataValue  = "val1"

      temporaryData = temporaryData + (newDataKey->newDataValue)

      val data: Vector[Either[WriteResponse, IamNotTheLeader]] = raftService.appendData(key = newDataKey, value = newDataValue)

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      localLeaderName       shouldBe temporaryFirstLeaderName
      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
    }

    "has all nodes synchronized with new data" in {

      freezeTest(seconds = Config.nodes * Config.heartbeatTimerInterval * 2, loggerMessage = "Waiting for sync data")

      val data: Vector[ActualData] = raftService.evaluateActualData
      log.info(s"Data: $data")
      val uniqueHashCodes: Vector[Int] = data.map(_.data.hashCode()).distinct

      uniqueHashCodes.size shouldBe 1
      uniqueHashCodes.head shouldBe temporaryData.hashCode()
    }

    "reject invalid client appendData message" in {
      val newDataKey = "key"
      val newDataValue = "key"

      val data: Vector[Either[WriteResponse, IamNotTheLeader]] = raftService.appendData(key = newDataKey, value = newDataValue, BigInt(101001))
      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
      data.filter(_.isLeft).head match {
        case Left(left) => left.success shouldBe false
        case _ => fail()
      }
    }

    "simulate leader crash" in {

      val data: Vector[Either[LeaderIsSimulatingCrash, IamNotTheLeader]] = raftService.simulateLeaderCrash()

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      localLeaderName       shouldBe temporaryFirstLeaderName
      data.count(_.isLeft)  shouldBe 1 //Only one leader
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

    "append new data in leader node" in {
      println("append new data in leader node")
      val newDataKey    = "key2"
      val newDataValue  = "val2"

      temporaryData = temporaryData + (newDataKey->newDataValue)
      val data: Vector[Either[WriteResponse, IamNotTheLeader]] = raftService.appendData(key = newDataKey, value = newDataValue)

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      localLeaderName       should not be temporaryFirstLeaderName
      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
    }

    "has all nodes synchronized with new data again" in {

      freezeTest(seconds = Config.nodes * Config.heartbeatTimerInterval, loggerMessage = "Waiting for sync data")

      val data: Vector[ActualData] = raftService.evaluateActualData

      val uniqueHashCodes: Vector[Int] = data.map(_.data.hashCode()).distinct

      uniqueHashCodes.size shouldBe 1
      uniqueHashCodes.head shouldBe temporaryData.hashCode()
    }

    "terminate actor system" in {

      raftService.terminate().map(response => response shouldBe true)
    }

  }

}