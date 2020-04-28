package nl.tudelft.htable.client.impl

import java.nio.ByteOrder

import akka.util.{ByteString, ByteStringBuilder}
import nl.tudelft.htable.core.TabletState.TabletState
import nl.tudelft.htable.core.{Node, Row, RowCell, RowMutation, RowRange, Tablet, TabletState}

/**
 * Helpers for reading and writing to the METADATA table.
 */
private[htable] object MetaHelpers {

  /**
   * Parse a row in the METADATA table.
   */
  def readRow(row: Row): Option[(Tablet, TabletState, Option[String])] = {
    for {
      startKey <- row.cells.find(_.qualifier == ByteString("start-key"))
      endKey <- row.cells.find(_.qualifier == ByteString("end-key"))
      table <- row.cells.find(_.qualifier == ByteString("table"))
      id = row.cells
        .find(_.qualifier == ByteString("id"))
        .map(_.value.iterator.getInt(ByteOrder.LITTLE_ENDIAN))
        .getOrElse(0)
      range = RowRange(startKey.value, endKey.value)
      targetTablet = Tablet(table.value.utf8String, range, id)
      state <- row.cells.find(_.qualifier == ByteString("state")).map(cell => TabletState(cell.value(0)))
      nodeUid = row.cells
        .find(_.qualifier == ByteString("node"))
        .map(_.value.utf8String)
    } yield (targetTablet, state, nodeUid)
  }

  /**
   * Write a row to the METADATA table.
   *
   * @param tablet The tablet to write.
   * @param state The state of the tablet.
   * @param node The node on which the tablet should be hosted.
   */
  def writeNew(tablet: Tablet, state: TabletState, node: Option[Node]): RowMutation = {
    val time = System.currentTimeMillis()
    val key = ByteString(tablet.table) ++ tablet.range.start
    val mutation = RowMutation("METADATA", key)
      .put(RowCell(ByteString("table"), time, ByteString(tablet.table)))
      .put(RowCell(ByteString("start-key"), time, tablet.range.start))
      .put(RowCell(ByteString("end-key"), time, tablet.range.end))
      .put(RowCell(ByteString("state"), time, ByteString(state.id)))
      .put(RowCell(ByteString("id"), time, new ByteStringBuilder().putInt(tablet.id)(ByteOrder.LITTLE_ENDIAN).result()))

    node match {
      case Some(value) => mutation.put(RowCell(ByteString("node"), time, ByteString(value.uid)))
      case None        => mutation
    }
  }

  /**
   * Write a row to the METADATA table based on its previous values.
   *
   * @param tablet The tablet to write.
   * @param state The state of the tablet.
   * @param node The node on which the tablet should be hosted.
   */
  def writeExisting(tablet: Tablet, state: TabletState, node: Option[Node]): RowMutation = {
    val time = System.currentTimeMillis()
    val key = ByteString(tablet.table) ++ tablet.range.start
    val mutation = RowMutation("METADATA", key)
      .put(RowCell(ByteString("state"), time, ByteString(state.id)))

    node match {
      case Some(value) => mutation.put(RowCell(ByteString("node"), time, ByteString(value.uid)))
      case None        => mutation
    }
  }
}
