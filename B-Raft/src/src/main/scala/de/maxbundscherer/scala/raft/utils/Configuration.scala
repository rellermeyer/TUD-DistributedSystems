package de.maxbundscherer.scala.raft.utils

import de.maxbundscherer.scala.raft.services.{BRaftService, RaftService}

import akka.japi.Util
import com.typesafe.config.ConfigList

trait Configuration {

  object Config {

    import com.typesafe.config.ConfigFactory

    private val raftPrototypeConfig = ConfigFactory.load().getConfig("raftPrototype")

    //Election Timer Min (Seconds)
    val electionTimerIntervalMin: Int = raftPrototypeConfig.getInt("electionTimerIntervalMin")

    //Election Timer Max (Seconds)
    val electionTimerIntervalMax: Int = raftPrototypeConfig.getInt("electionTimerIntervalMax")

    //Heartbeat Timer Interval (Seconds)
    val heartbeatTimerInterval: Int = raftPrototypeConfig.getInt("heartbeatTimerInterval")

    //Raft Nodes (Amount)
    val nodes : Int = raftPrototypeConfig.getInt("nodes")

    //Crash Interval (auto simulate crash after some heartbeats in LEADER behavior)
    val crashIntervalHeartbeats: Int = raftPrototypeConfig.getInt("crashIntervalHeartbeats")

    // Sleep downtime (Seconds) (after simulated crash in SLEEP behavior)
    val sleepDowntime: Int = raftPrototypeConfig.getInt("sleepDowntime")

    val raftTypeStr: String = raftPrototypeConfig.getString("raftType")

    val maxTerm: Int = raftPrototypeConfig.getInt("maxTerm")

    override def toString: String = {
      s"sleepDowntime=$sleepDowntime,raftTypeStr=$raftTypeStr,maxTerm=$maxTerm" +
      s"electionTimerIntervalMin=$electionTimerIntervalMin,electionTimerIntervalMax=$electionTimerIntervalMax," +
      s"heartbeatTimerInterval=$heartbeatTimerInterval,nodes=$nodes,crashIntervalHeartbeats=$crashIntervalHeartbeats"
    }
  }
}