package nl.tudelft.htable.core

import nl.tudelft.htable.core.Order

/**
 * A tablet is a subset of a table.
 */
final case class Tablet(table: String, range: RowRange, id: Int = 0) extends Ordered[Tablet] {
  override def compare(that: Tablet): Int = Order.tabletOrdering.compare(this, that)
}

object Tablet {

  /**
   * Obtain the root METADATA tablet.
   */
  val root: Tablet = Tablet("METADATA", RowRange.unbounded)

  /**
   * Determine if a [Tablet] is the root tablet.
   */
  def isRoot(tablet: Tablet): Boolean = isMeta(tablet) && !tablet.range.isLeftBounded

  /**
   * Determine if a [Tablet] is a metadata tablet.
   */
  def isMeta(tablet: Tablet): Boolean = tablet.table.equalsIgnoreCase("METADATA")
}
