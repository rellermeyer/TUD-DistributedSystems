package nl.tudelft.htable.core

import java.util.Comparator

import akka.util.ByteString

/**
 * An object in which the implicit key, row and cell orderings are defined.
 */
object Order {

  /**
   * Compare the specified objects if there is an implicit ordering available.
   */
  private def compare[T](x: T, y: T)(implicit ordering: Ordering[T]): Int = ordering.compare(x, y)

  /**
   * The key ordering of a row key.
   */
  implicit val keyOrdering: Ordering[ByteString] = new Ordering[ByteString] {
    override def compare(x: ByteString, y: ByteString): Int = {
      for ((bx, by) <- x.zip(y)) {
        val cmp = bx.compareTo(by)
        if (cmp != 0) {
          return cmp
        }
      }
      x.size - y.size
    }
  }

  /**
   * The default ordering of a tablet.
   */
  implicit val tabletOrdering: Ordering[Tablet] = Ordering
    .by[Tablet, String](_.table)
    .orElse(Ordering.comparatorToOrdering(new Comparator[Tablet] {
      def compare(l: Tablet, r: Tablet): Int = {
        // Fast path: pointer equality
        if (l eq r) {
          return 0
        }

        var result = Order.compare(l.range.start, r.range.start)

        if (result != 0) {
          return result
        }

        result = Order.compare(l.range.end, r.range.end)

        if (l.range.isLeftBounded && !l.range.isRightBounded)
          1 // This is the last region
        else if (r.range.isLeftBounded && !r.range.isRightBounded)
          -1 // r is the last region
        else
          result
      }
    }))

  /**
   * The default ordering of a row.
   */
  implicit val rowOrdering: Ordering[Row] = Ordering.by(_.key)

  /**
   * The default ordering of a row cell.
   */
  implicit val cellOrdering: Ordering[RowCell] = Ordering
    .by[RowCell, ByteString](_.qualifier)
    .orElseBy(_.timestamp)
}
