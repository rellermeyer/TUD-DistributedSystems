package nl.tudelft.htable.core

import akka.util.ByteString

/**
 * A logical row in a HTable table consisting of cells.
 */
final case class Row(key: ByteString, cells: Seq[RowCell]) extends Ordered[Row] {
  override def compare(that: Row): Int = Order.rowOrdering.compare(this, that)
}
