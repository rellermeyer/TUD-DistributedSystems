package nl.tudelft.htable.storage

import nl.tudelft.htable.core.Node

/**
 * Provider interface for a [StorageProvider] implementation.
 */
trait StorageDriverProvider {

  /**
   * Construct a [StorageProvider] for the specified node.
   */
  def create(node: Node): StorageDriver
}
