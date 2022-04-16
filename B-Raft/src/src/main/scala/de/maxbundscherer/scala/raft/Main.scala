package de.maxbundscherer.scala.raft

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.util.Timeout
import de.maxbundscherer.scala.raft.aggregates.Aggregate.ActualData
import de.maxbundscherer.scala.raft.services.RaftService
import kamon.Kamon
import de.maxbundscherer.scala.raft.utils.Configuration

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._


object Main extends App with Configuration {
  def checkFinished(data: Vector[ActualData]): Boolean = {
    log.info("Checking if data is consistent")
    val uniqueHashCodes: Vector[Int] = data.map(_.data.hashCode()).distinct
    uniqueHashCodes.size == 1 && data.head.data.size == 4
  }

  import de.maxbundscherer.scala.raft.services._
//  Kamon.init()
  private implicit val actorSystem: ActorSystem = ActorSystem("raftSystem")
  private implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  private implicit val timeout: Timeout = 15.seconds
  private val log: LoggingAdapter = actorSystem.log
  log.warning(s"Starting Main with Config:$Config")

  // No nice inheritance here unfortunately (in the interest of time...)
  if (Config.raftTypeStr == "Raft") {
    val raftService = new RaftService(numberNodes = Config.nodes)

    Thread.sleep(8000)

    raftService.appendData("x", "5")
    raftService.appendData("y", "4")
    raftService.appendData("z", "3")
    raftService.appendData("q", "2")

    Thread.sleep(5000)
    try {
      var data: Vector[ActualData] = raftService.evaluateActualData
      while(!checkFinished(data)) {
        Thread.sleep(10000)
        data = raftService.evaluateActualData
      }
      log.info(s"[VERIFY APPEND DATA], Consistent")
    }
    catch {
      case _ => log.info(s"[UNABLE TO VERIFY DUE TO TIMEOUT]")
    }

    log.warning("Press [Enter] to terminate actorSystem")
//    scala.io.StdIn.readLine()
    raftService.terminate()
  } else {
    val raftService = new BRaftService(numberNodes = Config.nodes)

    Thread.sleep(8000)

    raftService.appendBRaftData("x", "5")
    raftService.appendBRaftData("y", "4")
    raftService.appendBRaftData("z", "3")
    raftService.appendBRaftData("q", "2")

    Thread.sleep(5000)
    try {
      var data: Vector[ActualData] = raftService.evaluateActualData
      while(!checkFinished(data)) {
        Thread.sleep(10000)
        data = raftService.evaluateActualData
      }
      log.info(s"[VERIFY APPEND DATA], Consistent")
    }
    catch {
      case _ => log.info(s"[UNABLE TO VERIFY DUE TO TIMEOUT]")
    }

    log.warning("Press [Enter] to terminate actorSystem")
//    scala.io.StdIn.readLine()
    raftService.terminate()
  }
  Kamon.stop()
  System.exit(0)
}