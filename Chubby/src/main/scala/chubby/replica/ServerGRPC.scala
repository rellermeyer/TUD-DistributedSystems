package chubby.replica

import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.{ActorMaterializer, Materializer}
import chubby.grpc.ChubbyServiceHandler
import chubby.replica.grpc.ChubbyServiceImplementation
import chubby.replica.raft.ProtocolRaft
import chubby.replica.raft.ProtocolRaft.StartServer
import chubby.replica.raft.behavior.BehaviorInCreation

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

class ServerGRPC(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: ActorSystem = system
    implicit val mat: Materializer = ActorMaterializer()
    implicit val ec: ExecutionContext = sys.dispatcher

    val raftActor = sys.spawn(BehaviorInCreation(sys, Map.empty), "RaftActor")

    var allOtherReplica = List.empty[String]

    println(
      "Reading serverips.txt for replica IPs. Add other replica by host, i.e. 192.168.1.1. Type start to start the server"
    )

    val bufferedSource = Source.fromFile("serverips.txt")
    for (line <- bufferedSource.getLines) {
      allOtherReplica = line :: allOtherReplica
    }

    bufferedSource.close

    Iterator
      .continually(scala.io.StdIn.readLine)
      .takeWhile(_ != "start")
      .foreach(input => {
        allOtherReplica = input :: allOtherReplica

        println("Add other replica by host i.e. 192.168.1.1. Type start to start the server")
      })

    allOtherReplica.foreach(otherReplica => raftActor.tell(ProtocolRaft.RaftServerAnnounce(otherReplica)))

    raftActor.tell(StartServer)

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      ChubbyServiceHandler(new ChubbyServiceImplementation(raftActor, sys, mat))

    // Bind service handler servers to localhost:8080/8081
    val binding = Http().bindAndHandleAsync(
      service,
      interface = "0.0.0.0",
      port = 8080,
      connectionContext = HttpConnectionContext()
    )

    // report successful binding
    binding.foreach { binding => println(s"gRPC server bound to: ${binding.localAddress}") }

    binding
  }
}
