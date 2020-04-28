package nl.tudelft.htable.server

import java.nio.ByteOrder

import akka.Done
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, DispatcherSelector}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, ByteStringBuilder}
import nl.tudelft.htable.client.HTableInternalClient
import nl.tudelft.htable.client.impl.MetaHelpers
import nl.tudelft.htable.core._

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * The admin actor handles administrative requests to the cluster, such as creating, deleting or
 * splitting a table.
 */
object AdminActor {

  /**
   * Commands accepted by this actor.
   */
  sealed trait Command

  /**
   * A command to enable the admin commands.
   */
  final case class Enable(client: HTableInternalClient) extends Command

  /**
   * Request the server to create a new table.
   */
  final case class CreateTable(name: String, promise: Promise[Done]) extends Command

  /**
   * Request the server to delete a table.
   */
  final case class DeleteTable(name: String, promise: Promise[Done]) extends Command

  /**
   * Request the server to invalidate the specified tablets.
   */
  final case class Balance(tablets: Set[Tablet], shouldInvalidate: Boolean, promise: Promise[Done]) extends Command

  /**
   * Events emitted by this actor.
   */
  sealed trait Event

  /**
   * An event to indicate that the specified tablets should be refreshed.
   */
  final case class Balanced(tablets: Set[Tablet], shouldInvalidate: Boolean = false) extends Event

  /**
   * Construct the behavior of the admin actor.
   */
  def apply(listener: ActorRef[Event]): Behavior[Command] = disabled(listener)

  /**
   * Construct the behavior of the admin actor when the endpoint is disabled.
   *
   * @param listener The listener to emit events to.
   */
  def disabled(listener: ActorRef[Event]): Behavior[Command] = Behaviors.setup { context =>
    Behaviors.receiveMessagePartial {
      case Enable(client) =>
        context.log.info("Enabling admin endpoint")
        enabled(listener, client)
      case CreateTable(_, promise) =>
        promise.failure(new IllegalStateException("Admin endpoint not enabled for node"))
        Behaviors.same
      case DeleteTable(_, promise) =>
        promise.failure(new IllegalStateException("Admin endpoint not enabled for node"))
        Behaviors.same
      case Balance(_, _, promise) =>
        promise.failure(new IllegalStateException("Admin endpoint not enabled for node"))
        Behaviors.same
    }
  }

  /**
   * Construct the behavior of the admin actor when the endpoint is enabled.
   *
   * @param listener The listener to emit events to.
   * @param client The client to communicate with other nodes.
   */
  def enabled(listener: ActorRef[Event], client: HTableInternalClient): Behavior[Command] = Behaviors.setup { context =>
    implicit val mat: Materializer = Materializer(context.system)
    implicit val ec: ExecutionContext = context.system.dispatchers.lookup(DispatcherSelector.default())
    Behaviors.receiveMessagePartial {
      case Enable(client) =>
        context.log.debug("Re-enabling admin actor")
        enabled(listener, client)
      case CreateTable(table, promise) =>
        context.log.info(s"Creating new table $table")

        // We create a table by adding an entry for the table to the METADATA table
        // After we invalidate the current tablet distribution, it will automatically be detected by the load balancer
        // and assigned to some node which will actually create the table on disk.
        val tablet = Tablet(table, RowRange.unbounded, 0)
        val mutation = MetaHelpers.writeNew(tablet, TabletState.Created, None)
        client
          .mutate(mutation)
          .onComplete { res =>
            client.balance(Set(tablet))
            promise.complete(res)
          }
        Behaviors.same
      case DeleteTable(table, promise) =>
        context.log.info(s"Deleting table $table")

        // We delete a table by scanning for all its entries in the METADATA table and removing them.
        // Note that this does not yet remove the table from disk.
        if (table.equalsIgnoreCase("METADATA")) {
          promise.failure(new IllegalArgumentException("Refusing to remove METADATA"))
        } else {
          context.log.debug(s"Cleaning $table")
          val future = for {
            _ <- client
              .read(Scan(table, RowRange.unbounded))
              .mapAsyncUnordered(8)(row => client.mutate(RowMutation(table, row.key).delete()))
              .runFold(Done)((_, _) => Done)
            _ = context.log.debug(s"Updating METADATA entries for $table")
            _ <- client
              .read(Scan("METADATA", RowRange.leftBounded(ByteString(table))))
              .takeWhile { row =>
                val res = row.cells
                  .find(_.qualifier == ByteString("table"))
                  .map(_.value.utf8String)
                  .exists(_.equalsIgnoreCase(table))
                res
              }
              .mapAsyncUnordered(8) { row =>
                MetaHelpers.readRow(row) match {
                  case Some((tablet, _, _)) =>
                    context.log.debug("Updating state of METADATA row")
                    val mutation = MetaHelpers.writeExisting(tablet, TabletState.Deleted, None)
                    client.mutate(mutation)
                  case None =>
                    context.log.warn("Row in METADATA cannot be parsed")
                    Future.successful(Done)
                }

              }
              .runFold(Done)((_, _) => Done)
          } yield Done

          future.onComplete { res =>
            if (res.isFailure) {
              context.log.error("Failed to complete removal", res.failed.get)
            } else {
              context.log.debug(s"Successfully deleted table $table")
            }
            listener ! Balanced(Set.empty)
            promise.complete(res)
          }
        }
        Behaviors.same
      case Balance(tablets, shouldInvalidate, promise) =>
        listener ! Balanced(tablets, shouldInvalidate)
        promise.success(Done)
        Behaviors.same
    }
  }
}
