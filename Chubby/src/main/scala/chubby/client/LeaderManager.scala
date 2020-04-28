package chubby.client

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import akka.stream.ActorMaterializer
import chubby.grpc.{ChubbyService, ChubbyServiceClient, LeaderRequest}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Random, Success}

class LeaderManager(val identifier: String, val replicaAddresses: List[String]) {
  implicit val sys: ActorSystem = ActorSystem("System")
  implicit val mat: ActorMaterializer = ActorMaterializer()
//  implicit val ec: ExecutionContextExecutor = sys.dispatcher
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val random = new Random
  var leaderAddressFuture: Future[ChubbyService] = _

  def createReplicaSettings(address: String, port: Integer = 8080, withTls: Boolean = false): ChubbyService = {
    println(s"Creating Chubby service for '${address}'")
    val clientSettings = GrpcClientSettings.connectToServiceAt(address, port).withTls(withTls)
    ChubbyServiceClient(clientSettings)
  }

  def determineLeaderService(): Future[ChubbyService] = {
    if (this.leaderAddressFuture == null) {
      this.leaderAddressFuture = Future[ChubbyService] {

        println("Start with leader determination using exponential backoff.")

        this.createReplicaSettings(
          this.retryExponentialBackoff()(
            this.determineLeaderAddress(this.replicaAddresses)
          )
        )
      }
    }

    this.leaderAddressFuture
  }

  @scala.annotation.tailrec
  private def determineLeaderAddress(possibleAddresses: List[String]): String = {
    if (possibleAddresses.isEmpty) {
      throw new Exception(s"[${System.currentTimeMillis()}] There is no possible leader address available!")
    }

    val nextPossibleLeaderAddress: String = possibleAddresses(this.random.nextInt(possibleAddresses.length))
    val client: ChubbyService = this.createReplicaSettings(nextPossibleLeaderAddress)
    println(s"[${System.currentTimeMillis()}] Trying to determine leader at '${nextPossibleLeaderAddress}'.")

    val response = client.requestLeader(LeaderRequest(this.identifier))
    val result = scala.concurrent.Await.result(response, Duration.create(10, TimeUnit.SECONDS))

    if (result.isLeader) {
      println(s"[${System.currentTimeMillis()}] Determined the leaders address to be '${result.leaderAddress}'")
      result.leaderAddress
    } else if (possibleAddresses.length <= 1) {
      throw new Exception("[${System.currentTimeMillis()}] No more addresses left to determine leader from!")
    } else {
      this.determineLeaderAddress(possibleAddresses.filter(_ == nextPossibleLeaderAddress))
    }
  }

  @annotation.tailrec
  final def retryExponentialBackoff[T](t: Long = 2, n: Int = 1)(fn: => T): T = {
    util.Try {
      println(s"Start iteration '${n}' of exponential backoff.")
      fn
    } match {
      case util.Success(x) => x
      case _ =>
        Thread.sleep(scala.math.pow(t, n).toInt)
        retryExponentialBackoff(t, n + 1)(fn)
    }
  }
}
