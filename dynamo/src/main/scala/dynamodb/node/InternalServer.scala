package dynamodb.node

import akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.Materializer

import scala.concurrent.Future
import scala.util.{Failure, Success}

object InternalServer {
  def apply(valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int, nodeName: String): Behavior[Command] =
    Behaviors.setup(context => new InternalServer(context, valueRepository, host, port, nodeName))

  sealed trait Command
  final case class Started(binding: ServerBinding) extends Command
  final case class StartFailed(cause: Throwable) extends Command
  final case class Stop() extends Command
}

class InternalServer(context: ActorContext[InternalServer.Command], valueRepository: ActorRef[ValueRepository.Command], host: String, port: Int, nodeName: String)
  extends AbstractBehavior[InternalServer.Command](context) {

  import InternalServer._

  implicit val actorSystem: ActorSystem[Nothing] = context.system
  implicit val classicActorSystem: actor.ActorSystem = context.system.toClassic
  implicit val materializer: Materializer = Materializer(classicActorSystem)

  val internalRoutes = new InternalRoutes(valueRepository, nodeName)

  var started = false
  var binding: ServerBinding = _

  val serverBinding: Future[Http.ServerBinding] =
    Http.apply().bindAndHandle(internalRoutes.theInternalValueRoutes, host, port)

  context.pipeToSelf(serverBinding) {
    case Success(binding) => Started(binding)
    case Failure(ex) => StartFailed(ex)
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case Started(binding) =>
        started = true
        this.binding = binding
        context.log.debug(
          "Internal server online at http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)

        this

      case Stop() =>
        if (started) this.binding.unbind()
        context.log.debug(
          "Stopping server http://{}:{}/",
          binding.localAddress.getHostString,
          binding.localAddress.getPort)
        Behaviors.same
      case StartFailed(ex) =>
        throw new RuntimeException("Interal Server failed to start", ex)
    }
  }
}
