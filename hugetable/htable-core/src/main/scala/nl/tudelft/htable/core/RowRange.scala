package nl.tudelft.htable.core

import akka.util.ByteString

/**
 * A concrete range for [ByteString]s.
 *
 * @param start The start of the range (inclusive).
 * @param end The end of the range (exclusive).
 */
final case class RowRange(start: ByteString, end: ByteString) {

  /**
   * Determine whether this row range is left bounded.
   */
  def isLeftBounded: Boolean = start.nonEmpty

  /**
   * Determine whether this row range is right bounded.
   */
  def isRightBounded: Boolean = end.nonEmpty

  /**
   * Determine whether this row range is unbounded.
   */
  def isUnbounded: Boolean = !isLeftBounded && !isRightBounded
}

object RowRange {

  /**
   * Obtain an unbounded [RowRange].
   */
  val unbounded: RowRange = RowRange(ByteString.empty, ByteString.empty)

  /**
   * Obtain a [RowRange] that is left bounded (inclusive).
   */
  def leftBounded(start: ByteString): RowRange = RowRange(start, ByteString.empty)

  /**
   * Obtain a [RowRange] that is right bounded (exclusive).
   */
  def rightBounded(end: ByteString): RowRange = RowRange(ByteString.empty, end)

  /**
   * Obtain a [RowRange] of a prefix.
   */
  def prefix(prefix: ByteString): RowRange = {
    // Essentially we are treating it like an 'unsigned very very long' and doing +1 manually.
    // Search for the place where the trailing 0xFFs start
    var offset = prefix.length
    while (offset > 0 && prefix(offset - 1) == 0xFF.toByte) {
      offset -= 1
    }

    if (offset == 0) {
      // We got an 0xFFFF... (only FFs) stopRow value which is
      // the last possible prefix before the end of the table.
      // So set it to stop at the 'end of the table'
      return RowRange(prefix, ByteString.empty)
    }

    // Copy the right length of the original
    val newStopRow: Array[Byte] = prefix
      .slice(0, offset)
      .updated(offset - 1, (prefix(offset - 1) + 1).toByte)
      .toArray

    RowRange(prefix, ByteString.fromArrayUnsafe(newStopRow))
  }
}
