package nl.tudelft.htable.storage.mem

import nl.tudelft.htable.core.{Node, Tablet}
import nl.tudelft.htable.storage.{StorageDriver, TabletDriver}

/**
 * A volatile [StorageDriver] that stores a tablet entirely in memory.
 */
class InMemoryStorageDriver extends StorageDriver {

  /**
   * Open the specified tablet.
   */
  override def openTablet(tablet: Tablet): TabletDriver = createTablet(tablet)

  /**
   * Create a new tablet.
   */
  override def createTablet(tablet: Tablet): TabletDriver = new InMemoryTabletDriver(tablet)

  override def close(): Unit = {}
}
