package nl.tudelft.htable.client

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.{Done, NotUsed}
import nl.tudelft.htable.client.impl.{DefaultServiceResolverImpl, HTableClientImpl}
import nl.tudelft.htable.core._
import org.apache.curator.framework.CuratorFramework

import scala.concurrent.Future

/**
 * A client interface for accessing and operating on a HTable cluster.
 */
trait HTableClient {

  /**
   * Create a new table.
   *
   * @param name The name of the table to create.
   */
  def createTable(name: String): Future[Done]

  /**
   * Delete a table.
   *
   * @param name The name of the table to delete.
   */
  def deleteTable(name: String): Future[Done]

  /**
   * Split a tablet in the database.
   *
   * @param table The table to split.
   * @param splitKey The key at which to split the tablet.
   */
  def split(table: String, splitKey: ByteString): Future[Done]

  /**
   * Balance the given set of tablets.
   */
  def balance(tablets: Set[Tablet] = Set.empty, shouldInvalidate: Boolean = false): Future[Done]

  /**
   * Query the rows of a table on a particular node.
   */
  def read(node: Node, query: Query): Source[Row, NotUsed]

  /**
   * Query the rows of a table.
   */
  def read(query: Query): Source[Row, NotUsed]

  /**
   * Perform a mutation on a row on a particular node.
   */
  def mutate(node: Node, mutation: RowMutation): Future[Done]

  /**
   * Perform a mutation on a row.
   */
  def mutate(mutation: RowMutation): Future[Done]

  /**
   * Resolve the location of a particular tablet.
   *
   * @param tablet The tablet to resolve the location of.
   */
  def resolve(tablet: Tablet): Source[(Node, Tablet), NotUsed]

  /**
   * Close the connection to the cluster asynchronously and returns a [Future]
   * that completes when the client closed.
   */
  def close(): Future[Done]

  /**
   * Return a [Future] that completes when the client is closed.
   */
  def closed(): Future[Done]
}

object HTableClient {

  /**
   * Construct a [HTableClient] using the given ZooKeeper client.
   */
  def apply(zookeeper: CuratorFramework, resolver: ServiceResolver): HTableClient =
    new HTableClientImpl(zookeeper, ActorSystem("client"), true, resolver)

  /**
   * Construct a [HTableClient] using the given ZooKeeper client.
   */
  def apply(zookeeper: CuratorFramework): HTableClient = {
    val system = ActorSystem("client")
    new HTableClientImpl(zookeeper, system, true, new CachingServiceResolver(new DefaultServiceResolverImpl(system)))
  }

  /**
   * Construct a [HTableClient] using the given ZooKeeper client.
   */
  private[htable] def createInternal(zookeeper: CuratorFramework,
                                     actorSystem: ActorSystem,
                                     resolver: ServiceResolver): HTableInternalClient =
    new HTableClientImpl(zookeeper, actorSystem, false, resolver)
}
