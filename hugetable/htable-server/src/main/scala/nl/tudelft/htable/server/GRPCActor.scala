package nl.tudelft.htable.server

import java.net.InetSocketAddress

import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.grpc.scaladsl.ServiceHandler
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.{Http, HttpConnectionContext}
import akka.stream.Materializer
import nl.tudelft.htable.protocol.admin.{AdminService, AdminServiceHandler}
import nl.tudelft.htable.protocol.client.{ClientService, ClientServiceHandler}
import nl.tudelft.htable.protocol.internal.{InternalService, InternalServiceHandler}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * Actor that exposes the HugeTable server as an gRPC endpoint.
 */
object GRPCActor {

  /**
   * Commands accepted by this actor.
   */
  sealed trait Command

  /**
   * Internal message indicating that the gRPC service is up.
   */
  private final case class Up(binding: Http.ServerBinding) extends Command

  /**
   * Internal message indicating that the gRPC service is down.
   */
  private final case class Down(throwable: Throwable) extends Command

  /**
   * Events emitted by this actor.
   */
  sealed trait Event

  /**
   * The service has become active.
   */
  final case object ServiceActive extends Event

  /**
   * Construct the behavior of the gRPC actor.
   *
   * @param address The address to listen at.
   * @param clientService The client service implementation to use.
   * @param adminService The admin service implementation to use.
   * @param internalService The internal service implementation to use.
   * @param listener The listener to emit events to.
   */
  def apply(address: InetSocketAddress,
            clientService: ClientService,
            adminService: AdminService,
            internalService: InternalService,
            listener: ActorRef[Event]): Behavior[Command] = Behaviors.setup { context =>
    context.log.info("Starting gRPC services")
    context.pipeToSelf(createServices(address, context, clientService, adminService, internalService)) {
      case Success(value) => Up(value)
      case Failure(e)     => Down(e)
    }

    Behaviors.receiveMessagePartial {
      case Up(binding) =>
        context.log.info(s"Listening to ${binding.localAddress}")
        listener ! ServiceActive
        Behaviors.receiveSignal {
          case (_, PostStop) =>
            binding.terminate(10.seconds)
            Behaviors.stopped
        }
      case Down(throwable) =>
        context.log.error("Failed to start gRPC services", throwable)
        Behaviors.stopped
    }
  }

  /**
   * Create the gRPC services.
   */
  private def createServices(address: InetSocketAddress,
                             context: ActorContext[Command],
                             clientService: ClientService,
                             adminService: AdminService,
                             internalService: InternalService): Future[Http.ServerBinding] = {
    // Akka boot up code
    implicit val sys: akka.actor.ActorSystem = context.system.toClassic
    implicit val mat: Materializer = Materializer(context.system)
    implicit val ec: ExecutionContext =
      context.system.dispatchers.lookup(DispatcherSelector.default())

    val client = ClientServiceHandler.partial(clientService)
    val admin = AdminServiceHandler.partial(adminService)
    val internal = InternalServiceHandler.partial(internalService)

    // Create service handlers
    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(client, admin, internal)

    // Bind service handler servers to the specified address
    Http()(sys).bindAndHandleAsync(
      service,
      interface = address.getHostString,
      port = address.getPort,
      connectionContext = HttpConnectionContext()
    )
  }
}
