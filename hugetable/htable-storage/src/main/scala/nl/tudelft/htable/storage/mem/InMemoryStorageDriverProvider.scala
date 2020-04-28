package nl.tudelft.htable.storage.mem

import nl.tudelft.htable.core.Node
import nl.tudelft.htable.storage.{StorageDriver, StorageDriverProvider}

/**
 * A [StorageDriverProvider] for the [InMemoryStorageDriver].
 */
object InMemoryStorageDriverProvider extends StorageDriverProvider {

  /**
   * Construct a [StorageProvider] for the specified node.
   */
  override def create(node: Node): StorageDriver = new InMemoryStorageDriver()
}
