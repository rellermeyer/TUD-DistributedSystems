package nl.tudelft.htable.server.util

import akka.stream.scaladsl.Source
import akka.{Done, NotUsed}
import nl.tudelft.htable.client.ServiceResolver
import nl.tudelft.htable.core.Node
import nl.tudelft.htable.protocol.admin._
import nl.tudelft.htable.protocol.client._
import nl.tudelft.htable.protocol.internal._

import scala.concurrent.{Future, Promise}

/**
 * A [ServiceResolver] that resolves its own node to the internal implementations.
 */
class ServerServiceResolver(val self: Node,
                            val delegate: ServiceResolver,
                            val clientService: ClientService,
                            val adminService: AdminService,
                            val internalService: InternalService)
    extends ServiceResolver {
  private val promise = Promise[Done]()

  /**
   * Open the client service for the specified [Node].
   */
  override def openClient(node: Node): ClientServiceClient = {
    if (node == self)
      ClientServiceClientImpl
    else
      delegate.openClient(node)
  }

  /**
   * Open the admin service for the specified [Node].
   */
  override def openAdmin(node: Node): AdminServiceClient = {
    if (node == self)
      AdminServiceClientImpl
    else
      delegate.openAdmin(node)
  }

  /**
   * Open the internal service for the specified [Node].
   */
  override def openInternal(node: Node): InternalServiceClient = {
    if (node == self)
      InternalServiceClientImpl
    else
      delegate.openInternal(node)
  }

  override def close(): Unit = {
    delegate.close()
    promise.success(Done)
  }

  private object ClientServiceClientImpl extends ClientServiceClient {
    override def close(): Future[Done] = {
      ServerServiceResolver.this.close()
      Future.successful(Done)
    }

    override def closed: Future[Done] = promise.future

    /**
     * Read the specified row (range) and stream back the response.
     */
    override def read(in: ReadRequest): Source[ReadResponse, NotUsed] = clientService.read(in)

    /**
     * Mutate the specified row atomically.
     */
    override def mutate(in: MutateRequest): Future[MutateResponse] = clientService.mutate(in)
  }

  private object AdminServiceClientImpl extends AdminServiceClient {
    override def close(): Future[Done] = {
      ServerServiceResolver.this.close()
      Future.successful(Done)
    }

    override def closed: Future[Done] = promise.future

    /**
     * Create a new table in the cluster.
     */
    override def createTable(in: CreateTableRequest): Future[CreateTableResponse] = adminService.createTable(in)

    /**
     * Delete a table in the cluster.
     */
    override def deleteTable(in: DeleteTableRequest): Future[DeleteTableResponse] = adminService.deleteTable(in)

    override def balance(in: BalanceRequest): Future[BalanceResponse] = adminService.balance(in)
  }

  private object InternalServiceClientImpl extends InternalServiceClient {
    override def close(): Future[Done] = {
      ServerServiceResolver.this.close()
      Future.successful(Done)
    }

    override def closed: Future[Done] = promise.future

    override def split(in: SplitRequest): Future[SplitResponse] = internalService.split(in)

    /**
     * Ping a node in the cluster.
     */
    override def ping(in: PingRequest): Future[PingResponse] = internalService.ping(in)

    /**
     * Query a node for the tablets it's serving.
     */
    override def report(in: ReportRequest): Future[ReportResponse] = internalService.report(in)

    /**
     * Assign the specified tablets to the node.
     */
    override def assign(in: AssignRequest): Future[AssignResponse] = internalService.assign(in)
  }
}
