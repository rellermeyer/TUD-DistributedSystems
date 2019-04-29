package main.scala.log

/**
  * WriteLogItem class.
  *
  * @param timeVector of type Long
  * @param replicaId  of type Char
  * @param operation  of type WriteOperation
  */
case class WriteLogItem(timeVector: Long, replicaId: Char, operation: WriteOperation)
