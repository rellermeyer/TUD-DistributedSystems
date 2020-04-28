package nl.tudelft.htable.storage.hbase

import com.typesafe.scalalogging.Logger
import nl.tudelft.htable.core.{Node, Tablet}
import nl.tudelft.htable.storage.{StorageDriver, TabletDriver}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.hbase.client.{ColumnFamilyDescriptorBuilder, RegionInfoBuilder, TableDescriptorBuilder}
import org.apache.hadoop.hbase.regionserver.{HRegion, HRegionFileSystem, MemStoreLAB}
import org.apache.hadoop.hbase.util.FSUtils
import org.apache.hadoop.hbase.wal.{WALFactory, WALSplitter}
import org.apache.hadoop.hbase.{HConstants, TableName}

/**
 * A [StorageDriver] that uses HBase.
 */
class HBaseStorageDriver(val node: Node, val fs: FileSystem) extends StorageDriver {
  private val logger = Logger[HBaseStorageDriver.type]

  // HBase requires the ChunkCreator to be initialized statically
  import org.apache.hadoop.hbase.regionserver.ChunkCreator
  ChunkCreator.initialize(MemStoreLAB.CHUNK_SIZE_DEFAULT, false, 0, 0, 0, null)

  private val conf = new Configuration(fs.getConf)
  private val rootDir = new Path("htable-regions")
  conf.set(HConstants.HBASE_DIR, rootDir.toString)
  // AsyncFSWAL does not work with Hadoop 3.1
  conf.set("hbase.wal.provider", "org.apache.hadoop.hbase.wal.FSHLogProvider")
  conf.setInt("hbase.regionserver.hlog.tolerable.lowreplication", 1)
  private val factory =
    new WALFactory(conf, s"${node.address.getHostName}_${node.address.getPort}")
  private val walDir = new Path(rootDir, HConstants.HREGION_LOGDIR_NAME)
  private val logDir = new Path(walDir, factory.getFactoryId)
  // We must split the WAL log before we are able to reopen the region
  if (fs.exists(logDir)) {
    logger.info("WAL exists: splitting log")
    WALSplitter.split(rootDir, logDir,
      new Path(rootDir, HConstants.HREGION_OLDLOGDIR_NAME), fs, conf, factory)
  }

  override def openTablet(tablet: Tablet): TabletDriver = createTablet(tablet, open = true)

  override def createTablet(tablet: Tablet): TabletDriver = createTablet(tablet, open = false)

  private def createTablet(tablet: Tablet, open: Boolean): TabletDriver = {
    val tableName = TableName
      .valueOf(tablet.table)
    val tableDescriptor = TableDescriptorBuilder
      .newBuilder(tableName)
      .setColumnFamily(HBaseStorageDriver.columnFamily)
      .build

    var info = RegionInfoBuilder
      .newBuilder(tableName)
      .setReplicaId(0)
      .setRegionId(tablet.id)
      .setStartKey(tablet.range.start.toArray)
      .setEndKey(tablet.range.end.toArray)
      .setSplit(false)
      .setOffline(false)
      .build

    // Load persisted region info from disk if possible
    val regionDir = FSUtils.getRegionDirFromRootDir(rootDir, info)
    if (open && fs.exists(new Path(regionDir, HRegionFileSystem.REGION_INFO_FILE))) {
      logger.info(s"Reopening tablet $tablet: .regioninfo exists on filesystem")
      info = HRegionFileSystem.loadRegionInfoFileContent(fs, regionDir)
    }

    val WAL = factory.getWAL(info)
    val region =
      if (open)
        HRegion.openHRegion(conf, FileSystem.get(conf), rootDir, info, tableDescriptor, WAL)
      else
        HRegion.createHRegion(info, rootDir, conf, tableDescriptor, WAL, true)
    new HBaseTabletDriver(conf, region, tablet)
  }

  override def close(): Unit = {
    factory.shutdown()
  }
}

object HBaseStorageDriver {
  private[hbase] val columnFamily =
    ColumnFamilyDescriptorBuilder
      .newBuilder("hregion".getBytes("UTF-8"))
      .setMaxVersions(5) // TODO Add option for specifying this
      .build()
}
