package nl.tudelft.htable.client

import java.io.Closeable

import nl.tudelft.htable.core.Node
import nl.tudelft.htable.protocol.admin.AdminServiceClient
import nl.tudelft.htable.protocol.client.ClientServiceClient
import nl.tudelft.htable.protocol.internal.InternalServiceClient

/**
 * Interface used for resolving the underlying gRPC services.
 */
private[htable] trait ServiceResolver extends Closeable {

  /**
   * Open the client service for the specified [Node].
   */
  def openClient(node: Node): ClientServiceClient

  /**
   * Open the admin service for the specified [Node].
   */
  def openAdmin(node: Node): AdminServiceClient

  /**
   * Open the internal service for the specified [Node].
   */
  def openInternal(node: Node): InternalServiceClient
}
