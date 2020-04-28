package nl.tudelft.htable.server.services

import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import akka.stream.scaladsl.Source
import akka.util.Timeout
import akka.{Done, NotUsed}
import nl.tudelft.htable.core.Row
import nl.tudelft.htable.protocol.client._
import nl.tudelft.htable.server.NodeActor

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Implementation of the gRPC [ClientService].
 */
private[htable] class ClientServiceImpl(handler: ActorRef[NodeActor.Command])(implicit val sys: ActorSystem[Nothing])
    extends ClientService {
  implicit val timeout: Timeout = 3.seconds
  implicit val ec: ExecutionContext = sys.dispatchers.lookup(DispatcherSelector.default())

  /**
   * Read the specified row (range) and stream back the response.
   */
  override def read(in: ReadRequest): Source[ReadResponse, NotUsed] = {
    in.query match {
      case Some(value) =>
        val promise = Promise[Source[Row, NotUsed]]
        handler ! NodeActor.Read(value, promise)
        Source
          .future(promise.future)
          .flatMapConcat(identity)
          .grouped(5)
          .map(rows => ReadResponse(rows))
      case None =>
        Source.empty
    }

  }

  /**
   * Mutate a specified row in a table.
   */
  override def mutate(in: MutateRequest): Future[MutateResponse] = {
    in.mutation match {
      case Some(value) =>
        val promise = Promise[Done]
        handler ! NodeActor.Mutate(value, promise)
        promise.future.map(_ => MutateResponse())
      case None =>
        Future.successful(MutateResponse())
    }
  }
}
