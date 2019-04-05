package nl.tudelft.globule

import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import nl.tudelft.globule.RequestManager.HandleHttpRequest

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

object WebServer {
  val DEFAULT_PORT = 8082

  def run(port: Int, requestManager: ActorRef, interface: String): Unit = {
    implicit val system: ActorSystem = ActorSystem("globule")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val createRequestHandler = { address: InetSocketAddress => { request: HttpRequest =>
      implicit val timeout: Timeout = Timeout(Configs.app.getInt("webserver-timeout") seconds)
      val future = requestManager ? HandleHttpRequest(request, address)
      future.asInstanceOf[Future[HttpResponse]]
    }
    }

    // allow connections from any IP
    Http().bind(interface, port).runWith(Sink foreach { conn =>
      val address = conn.remoteAddress
      conn.handleWithAsyncHandler(createRequestHandler(address))
    })
    println(s"Server online at http://" + interface + ":" + port + "/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return

  }

}