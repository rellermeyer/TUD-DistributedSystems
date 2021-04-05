package main.scala.log

/**
  * WriteOperation class.
  *
  * @param key       the key that has been changed
  * @param operation the operation used to change the key
  * @param value     the value which the key has been changed
  */
case class WriteOperation(key: Char, operation: Char, value: Int)
