package sgrub.console

import scala.collection.mutable
import scala.io.StdIn

object BatchReader {
  def readBatch(): Map[Long, Array[Byte]] = {
    var result = mutable.Map.empty[Long, Array[Byte]]
    do {
      println("Key:")
      val key = StdIn.readLong()
      println("Value:")
      val value = StdIn.readLine()
      result(key) = value.getBytes()
    } while ({
      println("Continue entering data? (y/n)")
      StdIn.readBoolean()
    })
    result.toMap
  }
}
