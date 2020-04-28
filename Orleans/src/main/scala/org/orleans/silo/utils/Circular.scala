package org.orleans.silo.utils

import scala.collection.mutable.Queue

/** Circular list. **/
class Circular[A](list: Seq[A]) extends Iterator[A] {
  val elements = new Queue[A] ++= list
  var pos = 0

  def next = {
    if (pos == elements.length)
      pos = 0
    val value = elements(pos)
    pos = pos + 1
    value
  }

  def hasNext = !elements.isEmpty
  def add(a: A): Unit = { elements += a }
  override def toString = elements.toString

}
