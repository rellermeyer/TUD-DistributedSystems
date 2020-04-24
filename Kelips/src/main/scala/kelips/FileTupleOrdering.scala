package main.scala.kelips

import kelips.FileTuple

/**
 * Ordering for FileTuple used in the AVL Tree.
 * Sort based on filename.
 */
object FileTupleOrdering extends Ordering[FileTuple] {
  def compare(a: FileTuple, b: FileTuple) = a.fileName compare b.fileName
}
