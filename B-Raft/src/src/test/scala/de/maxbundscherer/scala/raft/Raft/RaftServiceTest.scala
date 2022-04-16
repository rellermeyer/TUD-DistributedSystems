package de.maxbundscherer.scala.raft.Raft

import de.maxbundscherer.scala.raft.Raft.utils.BaseServiceTest
import de.maxbundscherer.scala.raft.aggregates.Aggregate._
import de.maxbundscherer.scala.raft.utils.Configuration

class RaftServiceTest extends BaseServiceTest with Configuration {

  import de.maxbundscherer.scala.raft.aggregates.RaftAggregate._

  /**
   * Freeze test (actorSystem is still working)
   * @param seconds Int
   * @param loggerMessage String
   */
  private def freezeTest(seconds: Int, loggerMessage: String): Unit = {

    log.warning(s"Test is in sleepMode for $seconds seconds ($loggerMessage)")
    Thread.sleep(seconds * 1000)
    log.warning(s"Test continues")

  }

  "RaftService" should {

    var temporaryFirstLeaderName: String    = ""
    var temporaryData: Map[String, String]  = Map.empty

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

      val newDataKey    = "key1"
      val newDataValue  = "val1"

      temporaryData = temporaryData + (newDataKey->newDataValue)

      val data: Vector[Either[WriteSuccess, IamNotTheLeader]] = raftService.appendData(key = newDataKey, value = newDataValue)

      val localLeaderName = data.filter(_.isLeft).head match {
        case Left(left) => left.actorName
        case _ => ""
      }

      localLeaderName       shouldBe temporaryFirstLeaderName
      data.count(_.isLeft)  shouldBe 1 //Only on leader
      data.count(_.isRight) shouldBe ( Config.nodes - 1 ) //Other nodes shouldBe follower
    }

    "has all nodes synchronized with new data" in {

      freezeTest(seconds = Config.nodes * Config.heartbeatTimerInterval, loggerMessage = "Waiting for sync data")

      val data: Vector[ActualData] = raftService.evaluateActualData

      val uniqueHashCodes: Vector[Int] = data.map(_.data.hashCode()).distinct

      uniqueHashCodes.size shouldBe 1
      uniqueHashCodes.head shouldBe temporaryData.hashCode()
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

    "append new data in leader node" in {

      val newDataKey    = "key2"
      val newDataValue  = "val2"

      temporaryData = temporaryData + (newDataKey->newDataValue)

      val data: Vector[Either[WriteSuccess, IamNotTheLeader]] = raftService.appendData(key = newDataKey, value = newDataValue)

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