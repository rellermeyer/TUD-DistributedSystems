package nl.tudelft.htable.server.services

import akka.Done
import akka.actor.typed.{ActorRef, ActorSystem, DispatcherSelector}
import akka.util.Timeout
import nl.tudelft.htable.protocol.admin._
import nl.tudelft.htable.server.AdminActor.Command
import nl.tudelft.htable.server.AdminActor

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * Implementation of the gRPC [AdminService].
 */
private[htable] class AdminServiceImpl(handler: ActorRef[Command])(implicit val sys: ActorSystem[Nothing])
    extends AdminService {
  implicit val timeout: Timeout = 3.seconds
  implicit val ec: ExecutionContext = sys.dispatchers.lookup(DispatcherSelector.default())

  /**
   * Create a new table in the cluster.
   */
  override def createTable(in: CreateTableRequest): Future[CreateTableResponse] = {
    val promise = Promise[Done]
    handler ! AdminActor.CreateTable(in.tableName, promise)
    promise.future.map(_ => CreateTableResponse())
  }

  /**
   * Delete a table in the cluster.
   */
  override def deleteTable(in: DeleteTableRequest): Future[DeleteTableResponse] = {
    val promise = Promise[Done]
    handler ! AdminActor.DeleteTable(in.tableName, promise)
    promise.future.map(_ => DeleteTableResponse())
  }

  override def balance(in: BalanceRequest): Future[BalanceResponse] = {
    val promise = Promise[Done]
    handler ! AdminActor.Balance(in.tablets.toSet, in.invalidate, promise)
    promise.future.map(_ => BalanceResponse())
  }
}
