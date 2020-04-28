package nl.tudelft.htable.storage.hbase

import java.util

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.Logger
import nl.tudelft.htable.core
import nl.tudelft.htable.core._
import nl.tudelft.htable.storage.TabletDriver
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.hbase.client.{Delete, Get, Put, RegionInfoBuilder, Scan}
import org.apache.hadoop.hbase.regionserver.{HRegion, HRegionFileSystem, Region, RegionScanner}
import org.apache.hadoop.hbase.{Cell, CellBuilderFactory, CellBuilderType, CellUtil}

import scala.jdk.CollectionConverters._

/**
 * An implementation of [TabletDriver] for HBase, corresponding to a single [HRegion].
 */
class HBaseTabletDriver(private val conf: Configuration, val region: HRegion, override val tablet: Tablet) extends TabletDriver {
  private val logger = Logger[HBaseStorageDriver.type]

  /**
   * Perform the specified mutation in the tablet.
   */
  override def mutate(mutation: RowMutation): Unit = {
    var shouldPut = false
    val put = new Put(mutation.key.toArray)

    var shouldDeleteColumn = false
    val deleteColumn = new Delete(mutation.key.toArray)

    var shouldDelete = false
    val delete = new Delete(mutation.key.toArray)

    mutation.mutations.foreach {
      case Mutation.PutCell(cell) =>
        shouldPut = true
        put.add(toHBase(mutation.key, cell))
      case Mutation.DeleteCell(cell) =>
        shouldDeleteColumn = true
        deleteColumn.addColumn("hregion".getBytes("UTF-8"), cell.qualifier.toArray)
      case Mutation.Delete =>
        shouldDelete = true
    }

    if (shouldPut) {
      region.startRegionOperation(Region.Operation.PUT)
      region.put(put)
      region.closeRegionOperation(Region.Operation.PUT)
    }

    if (shouldDeleteColumn) {
      region.startRegionOperation(Region.Operation.DELETE)
      region.delete(deleteColumn)
      region.closeRegionOperation(Region.Operation.DELETE)
    }

    if (shouldDelete) {
      region.startRegionOperation(Region.Operation.DELETE)
      region.delete(delete)
      region.closeRegionOperation(Region.Operation.DELETE)
    }
  }

  /**
   * Query the specified data in the tablet.
   */
  override def read(query: Query): Source[Row, NotUsed] = {
    Source.fromIterator[Row] { () =>
      query match {
        case core.Get(_, key) =>
          val get = new Get(key.toArray)
          get.setCacheBlocks(true) // Enable caching
          get.addFamily("hregion".getBytes("UTF-8"))
          get.readAllVersions()
          region.startRegionOperation(Region.Operation.GET)
          val result = region.get(get)
          region.closeRegionOperation(Region.Operation.GET)
          if (result.isEmpty)
            Iterator()
          else
            Iterator(Row(ByteString(result.getRow), result.listCells().asScala.map(fromHBase).toSeq))
        case core.Scan(_, range, reversed) =>
          // Note that the start/end row are also reversed when we scan in reverse order due
          // to HBase behavior
          val startRow = if (reversed) range.end.toArray else range.start.toArray
          val endRow = if (reversed) range.start.toArray else range.end.toArray
          val enableWorkaround = !region.getRegionInfo.containsRow(startRow)
          val scan = new Scan()
            .setCacheBlocks(true) // Enable caching
             // XXX Workaround for exlcusive start row not working
            .withStartRow(if (enableWorkaround) makeExclusive(range.end).toArray else startRow, !reversed || enableWorkaround)
            .withStopRow(endRow, reversed)
            .setReversed(reversed)
            .addFamily("hregion".getBytes("UTF-8"))
            .readAllVersions()

          val scanner = region.getScanner(scan).asInstanceOf[RegionScanner]

          new Iterator[Option[Row]] {
            var more = true
            val cells = new util.ArrayList[Cell]()
            override def hasNext: Boolean = more

            override def next(): Option[Row] = {
              cells.clear()

              region.startRegionOperation(Region.Operation.SCAN)
              try {
                more = scanner.nextRaw(cells)
              } finally {
                region.closeRegionOperation(Region.Operation.SCAN)
              }

              val scalaCells = cells.asScala
              scalaCells.headOption.map { value =>
                val rowKey = ByteString(CellUtil.cloneRow(value))
                val rowCells = scalaCells.map(fromHBase).toSeq
                Row(rowKey, rowCells)
              }
            }
          }.flatten
      }
    }
  }

  /**
   * Make the specified key exclusive.
   */
  private def makeExclusive(key: ByteString): ByteString = {
    if (key.isEmpty)
      key
    else if (key.last == 0x00.toByte)
      key.dropRight(1)
    else
      key.dropRight(1) ++ ByteString(key.last - 1)

  }

  override def split(splitKey: ByteString): (Tablet, Tablet) = {
    region.flush(true)

    logger.info(s"Splitting ${tablet} at ${splitKey}")

    val leftTablet = Tablet(tablet.table, RowRange(tablet.range.start, splitKey), tablet.id + 1)
    val leftDaughter = RegionInfoBuilder
      .newBuilder(region.getTableDescriptor.getTableName)
      .setStartKey(tablet.range.start.toArray)
      .setEndKey(splitKey.toArray)
      .setSplit(false)
      .setRegionId(region.getRegionInfo.getRegionId + 1)
      .build

    val rightTablet = Tablet(tablet.table, RowRange(splitKey, tablet.range.end), tablet.id + 1)
    val rightDaughter = RegionInfoBuilder
      .newBuilder(region.getTableDescriptor.getTableName)
      .setStartKey(splitKey.toArray)
      .setEndKey(tablet.range.end.toArray)
      .setSplit(false)
      .setRegionId(region.getRegionInfo.getRegionId + 1)
      .build

    val regionFs = region.getRegionFileSystem
    regionFs.createSplitsDir(leftDaughter, rightDaughter)
    SplitUtils.splitStores(region, leftDaughter, rightDaughter)

    regionFs.commitDaughterRegion(leftDaughter)
    regionFs.commitDaughterRegion(rightDaughter)

    logger.info(s"${tablet} split into $leftTablet and $rightTablet")

    (leftTablet, rightTablet)
  }


  override def close(delete: Boolean): Unit = {
    if (delete) {
      val fs = region.getFilesystem
      val tableDir = region.getRegionFileSystem.getTableDir
      val regionInfo = region.getRegionInfo

      region.close()

      // Delete the entire region directory
      HRegionFileSystem.deleteRegionFromFileSystem(conf, fs, tableDir, regionInfo)
    } else {
      // Force flush for now to not lose changes when terminating the process
      region.flush(true)
      region.close()
    }
  }

  /**
   * Convert to a HBase cell.
   */
  private def toHBase(row: ByteString, cell: RowCell): Cell = {
    val res = CellBuilderFactory.create(CellBuilderType.DEEP_COPY)
    res.setFamily("hregion".getBytes("UTF-8"))
    res.setType(Cell.Type.Put)
    res.setRow(row.toArray)
    res.setQualifier(cell.qualifier.toArray)
    res.setValue(cell.value.toArray)
    res.setTimestamp(cell.timestamp)
    res.build()
  }

  /**
   * Convert from a HBase cell.
   */
  private def fromHBase(cell: Cell): RowCell =
    RowCell(ByteString(CellUtil.cloneQualifier(cell)), cell.getTimestamp, ByteString(CellUtil.cloneValue(cell)))
}
