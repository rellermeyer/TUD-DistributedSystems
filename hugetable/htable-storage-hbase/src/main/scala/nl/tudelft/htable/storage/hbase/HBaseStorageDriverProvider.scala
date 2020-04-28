package nl.tudelft.htable.storage.hbase

import nl.tudelft.htable.core.Node
import nl.tudelft.htable.storage.{StorageDriver, StorageDriverProvider}
import org.apache.hadoop.fs.FileSystem

/**
 * A [StorageDriverProvider] for the HBase implementation.
 */
class HBaseStorageDriverProvider(val fs: FileSystem) extends StorageDriverProvider {

  /**
   * Construct a [StorageProvider] for the specified node.
   */
  override def create(node: Node): StorageDriver = new HBaseStorageDriver(node, fs)
}
