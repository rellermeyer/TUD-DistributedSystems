package core.data_structures

import java.nio.ByteBuffer
import java.security.MessageDigest

class Record(id: String, value: Long) {
  private val fieldId: String = id
  private val balance: Long = value

  def getBalance: Long = balance

  def getFieldId: String = fieldId

  override def equals(other: Any): Boolean = {
    other match {
      case otherRecord: Record =>
        fieldId == otherRecord.getFieldId && balance == otherRecord.getBalance
      case _ =>
        false
    }
  }

  override def hashCode(): Int = {
    val md = MessageDigest.getInstance("MD5")
    val input = this.toString().getBytes()
    ByteBuffer.wrap(md.digest(input)).getInt
  }

  override def toString: String = {
    fieldId + ", " + balance
  }
}
