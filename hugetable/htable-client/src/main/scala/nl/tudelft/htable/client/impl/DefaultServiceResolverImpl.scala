package nl.tudelft.htable.client.impl

import akka.actor.ActorSystem
import akka.grpc.GrpcClientSettings
import nl.tudelft.htable.client.ServiceResolver
import nl.tudelft.htable.core.Node
import nl.tudelft.htable.protocol.admin.AdminServiceClient
import nl.tudelft.htable.protocol.client.ClientServiceClient
import nl.tudelft.htable.protocol.internal.InternalServiceClient

import scala.concurrent.ExecutionContextExecutor

/**
 * Default implementation of [ServiceResolver].
 */
private[htable] class DefaultServiceResolverImpl(val actorSystem: ActorSystem) extends ServiceResolver {
  implicit val sys: ActorSystem = actorSystem
  implicit val ec: ExecutionContextExecutor = sys.dispatcher

  override def openClient(node: Node): ClientServiceClient = {
    val settings = GrpcClientSettings
      .connectToServiceAt(node.address.getHostString, node.address.getPort)
      .withTls(false)
    val client = ClientServiceClient(settings)
    client
  }

  override def openAdmin(node: Node): AdminServiceClient = {
    val settings = GrpcClientSettings
      .connectToServiceAt(node.address.getHostString, node.address.getPort)
      .withTls(false)
    AdminServiceClient(settings)
  }

  override def openInternal(node: Node): InternalServiceClient = {
    val settings = GrpcClientSettings
      .connectToServiceAt(node.address.getHostString, node.address.getPort)
      .withTls(false)
    InternalServiceClient(settings)
  }

  override def close(): Unit = {}
}
