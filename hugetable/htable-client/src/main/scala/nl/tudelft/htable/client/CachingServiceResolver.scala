package nl.tudelft.htable.client

import java.util.concurrent.ConcurrentHashMap

import nl.tudelft.htable.core.Node
import nl.tudelft.htable.protocol.admin.AdminServiceClient
import nl.tudelft.htable.protocol.client.ClientServiceClient
import nl.tudelft.htable.protocol.internal.InternalServiceClient

/**
 * A service resolver which caches the clients.
 */
class CachingServiceResolver(val delegate: ServiceResolver) extends ServiceResolver {
  private val clientCache = new ConcurrentHashMap[Node, ClientServiceClient]
  private val adminCache = new ConcurrentHashMap[Node, AdminServiceClient]
  private val internalCache = new ConcurrentHashMap[Node, InternalServiceClient]

  override def openClient(node: Node): ClientServiceClient = {
    clientCache.computeIfAbsent(node, node => delegate.openClient(node))
  }

  override def openAdmin(node: Node): AdminServiceClient = {
    adminCache.computeIfAbsent(node, node => delegate.openAdmin(node))
  }

  override def openInternal(node: Node): InternalServiceClient = {
    internalCache.computeIfAbsent(node, node => delegate.openInternal(node))
  }

  override def close(): Unit = {
    clientCache.forEach { (_, client) =>
      client.close()
    }
    clientCache.clear()
    adminCache.forEach { (_, client) =>
      client.close()
    }
    adminCache.clear()
    internalCache.forEach { (_, client) =>
      client.close()
    }
    internalCache.clear()
  }
}
