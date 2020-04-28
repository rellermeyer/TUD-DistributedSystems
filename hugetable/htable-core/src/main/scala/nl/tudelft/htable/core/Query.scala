package nl.tudelft.htable.core

import akka.util.ByteString

/**
 * A builder class for constructing a HTable query.
 *
 * @param table The name of the table to query.
 */
sealed abstract class Query(val table: String)

/**
 * A query to obtain a single row.
 */
final case class Get(override val table: String, key: ByteString) extends Query(table)

/**
 * A query to obtain a range of rows.
 */
final case class Scan(override val table: String, range: RowRange, reversed: Boolean = false) extends Query(table)
