package de.maxbundscherer.scala.raft.BRaft.utils

import de.maxbundscherer.scala.raft.services.{BRaftService, RaftService}
import akka.event.LoggingAdapter
import de.maxbundscherer.scala.raft.utils.Configuration
import org.scalatest.{AsyncWordSpec, Matchers}

object BaseServiceTest extends Configuration {

  import akka.actor.ActorSystem
  import akka.util.Timeout
  import scala.concurrent.ExecutionContextExecutor
  import scala.concurrent.duration._

  private implicit val actorSystem: ActorSystem = ActorSystem("testSystem")
  private implicit val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  private implicit val timeout: Timeout = 15.seconds
  private lazy val log: LoggingAdapter = actorSystem.log

  private lazy val raftService = new BRaftService(numberNodes = Config.nodes)

}

trait BaseServiceTest extends AsyncWordSpec with Matchers {

  val log: LoggingAdapter = BaseServiceTest.log
  val raftService: BRaftService = BaseServiceTest.raftService

  /**
    * Freeze test (actorSystem is still working)
    *
    * @param seconds       Int
    * @param loggerMessage String
    */
  def freezeTest(seconds: Int, loggerMessage: String): Unit = {
    log.warning(s"Test is in sleepMode for $seconds seconds ($loggerMessage)")
    Thread.sleep(seconds * 1000)
    log.warning(s"Test continues")
  }
}