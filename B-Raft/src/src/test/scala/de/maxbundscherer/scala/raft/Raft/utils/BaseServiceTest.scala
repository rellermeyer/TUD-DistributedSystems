package de.maxbundscherer.scala.raft.Raft.utils

import de.maxbundscherer.scala.raft.services.RaftService
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

  private lazy val raftService = new RaftService(numberNodes = Config.nodes)

}

trait BaseServiceTest extends AsyncWordSpec with Matchers {

  val log: LoggingAdapter = BaseServiceTest.log
  val raftService: RaftService = BaseServiceTest.raftService

}