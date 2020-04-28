package nl.tudelft.htable.storage.mem

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import nl.tudelft.htable.core._
import nl.tudelft.htable.storage.TabletDriver

import scala.collection.mutable

/**
 * A [TabletDriver] that stores the memory in a sorted map in memory.
 */
private[mem] class InMemoryTabletDriver(override val tablet: Tablet, initialCells: Map[ByteString, Row] = Map.empty)
    extends TabletDriver {

  /**
   * The map storing the cells.
   */
  private val map = new mutable.TreeMap[ByteString, Row]()(Order.keyOrdering)
  map.addAll(initialCells)

  /**
   * A flag to indicate the region is closed.
   */
  private var isClosed: Boolean = false

  /**
   * Perform the specified mutation in the tablet.
   */
  def mutate(mutation: RowMutation): Unit = {
    if (isClosed) {
      throw new IllegalStateException("Tablet is closed")
    }

    val row = map.getOrElse(mutation.key, Row(mutation.key, List()))
    val cells: mutable.TreeSet[RowCell] = new mutable.TreeSet()(Order.cellOrdering) ++ row.cells

    for (cellMutation <- mutation.mutations) {
      cellMutation match {
        case Mutation.PutCell(cell)    => cells += cell
        case Mutation.DeleteCell(cell) => cells -= cell
        case Mutation.Delete           => cells.clear()
      }
    }

    map(row.key) = row.copy(cells = cells.toSeq)
  }

  /**
   * Query the specified data in the tablet.
   */
  def read(query: Query): Source[Row, NotUsed] = {
    if (isClosed) {
      throw new IllegalStateException("Tablet is closed")
    }

    query match {
      case Get(_, key) =>
        map.get(key) match {
          case Some(value) => Source.single(value)
          case None        => Source.empty
        }
      case Scan(_, range, reversed) =>
        Source.fromIterator { () =>
          val submap =
            if (range.isUnbounded)
              map
            else if (!range.isLeftBounded)
              map.rangeTo(range.end)
            else
              map.rangeFrom(range.start)

          if (reversed) {
            submap.values.toSeq.reverseIterator
          } else {
            submap.valuesIterator
          }
        }
    }
  }

  override def split(splitKey: ByteString): (Tablet, Tablet) = {
    if (isClosed) {
      throw new IllegalStateException("Tablet is closed")
    }

    if (map.isEmpty) {
      throw new IllegalStateException("Cannot split on empty tablet")
    }

    close()

    val leftTablet = Tablet(tablet.table, RowRange(tablet.range.start, splitKey))
    val rightTablet = Tablet(tablet.table, RowRange(splitKey, tablet.range.end))

    (leftTablet, rightTablet)
  }

  override def close(delete: Boolean): Unit = {
    isClosed = true
  }
}
