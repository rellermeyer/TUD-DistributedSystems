package core.data_structures

import core.applications.Application._
import core.operations.Operation._

import java.nio.ByteBuffer
import java.security.MessageDigest
import scala.collection.immutable


class Transaction(clientSeq: Int, op: Operation, a1:String, a2: Option[String], rs: immutable.Seq[String], ws: immutable.Seq[String],
                  value: Long, application: Application) {
  private val seq: Int = clientSeq
  private val operation: Operation = op
  private val acc1: String = a1
  private val acc2: Option[String] = a2
  private val readSet: immutable.Seq[String] = rs
  private val writeSet: immutable.Seq[String] = ws
  private val amount: Long = value
  private val app: Application = application
  private val id: String = this.hash()

  def getSeq: Int = seq
  def getAcc1: String = acc1
  def getAcc2: Option[String] = acc2
  def getAmount: Long = amount
  def getApplication: Application = app
  def getId: String = id
  def getOperation: Operation = operation
  def getReadSet: immutable.Seq[String] = readSet
  def getWriteSet: immutable.Seq[String] = writeSet

  def getMD5: Array[Byte] = {
    val md = MessageDigest.getInstance("MD5")
    md.digest(this.toString().getBytes())
  }

  def hash(): String = {
    val res = this.getMD5
    new String(res)
  }

  override def equals(other: Any): Boolean = {
    other match {
      case otherTransaction: Transaction =>
        seq == otherTransaction.getSeq &&
          acc1.equals(otherTransaction.acc1) &&
          acc2.equals(otherTransaction.acc2) &&
          amount == otherTransaction.amount &&
          app.equals(otherTransaction.app) &&
          operation.equals(otherTransaction.operation)
      case _ =>
        false
    }
  }

  override def hashCode(): Int = {
    val res = this.getMD5
    ByteBuffer.wrap(res).getInt
  }

  override def toString: String = {
    seq + " " + operation + " " + acc1 + " " + acc2 + " " + amount + " " + app
  }
}
