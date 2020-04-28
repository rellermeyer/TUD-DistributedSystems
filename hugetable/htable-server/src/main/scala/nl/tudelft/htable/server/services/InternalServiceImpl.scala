package nl.tudelft.htable.server.services

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import akka.util.Timeout
import nl.tudelft.htable.core.{AssignType, Tablet}
import nl.tudelft.htable.protocol.internal._
import nl.tudelft.htable.server.NodeActor

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Implementation of the gRPC [InternalService].
 */
private[htable] class InternalServiceImpl(handler: ActorRef[NodeActor.Command])(implicit val sys: ActorSystem[Nothing])
    extends InternalService {
  // asking someone requires a timeout if the timeout hits without response
  // the ask is failed with a TimeoutException
  implicit val timeout: Timeout = 3.second
  implicit val ec: ExecutionContext = sys.dispatchers.lookup(DispatcherSelector.default())

  /**
   * Ping a node in the cluster.
   */
  override def ping(in: PingRequest): Future[PingResponse] = {
    val promise = Promise[Done]
    handler ! NodeActor.Ping(promise)
    promise.future.map(_ => PingResponse())
  }

  /**
   * Query a node for the tablets it's serving.
   */
  override def report(in: ReportRequest): Future[ReportResponse] = {
    val promise = Promise[Seq[Tablet]]
    handler ! NodeActor.Report(promise)
    promise.future.map(tablets => ReportResponse(tablets))
  }

  /**
   * Assign the specified tablets to the node.
   */
  override def assign(in: AssignRequest): Future[AssignResponse] = {
    val promise = Promise[Done]
    handler ! NodeActor.Assign(in.actions, promise)
    promise.future.map(_ => AssignResponse())
  }

  /**
   * Perform a split of the specified table.
   */
  override def split(in: SplitRequest): Future[SplitResponse] = {
    val promise = Promise[Done]
    handler ! NodeActor.Split(in.tableName, in.splitKey, promise)
    promise.future.map(_ => SplitResponse())
  }
}
