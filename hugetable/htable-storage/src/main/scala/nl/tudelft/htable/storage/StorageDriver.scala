package nl.tudelft.htable.storage

import java.io.Closeable

import nl.tudelft.htable.core.{Node, Tablet}

/**
 * A storage driver manages the storage and retrieval of data stored in the HTable database.
 */
trait StorageDriver extends Closeable {

  /**
   * Open the specified tablet.
   *
   * @param tablet The tablet to open.
   */
  def openTablet(tablet: Tablet): TabletDriver

  /**
   * Create a new tablet.
   *
   * @param tablet The tablet to create.
   */
  def createTablet(tablet: Tablet): TabletDriver
}
