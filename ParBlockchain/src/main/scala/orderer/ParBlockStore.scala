package orderer

import core.data_structures.Transaction
import core.Config

import ckite.rpc.{WriteCommand, ReadCommand}
import ckite.statemachine._
import java.nio.ByteBuffer

class ParBlockStore(ord: Order) extends StateMachine {
  private var lastIndex: Long = 0
  private val orderer = ord

  // Called when a read command has been received
  def applyRead: PartialFunction[ReadCommand[_], Option[Transaction]] = {
    case Get(key) => None
  }

  // Called when a consensus has been reached for a WriteCommand
  // index associated to the write is provided to implement your own persistent semantics
  // see lastAppliedIndex
  def applyWrite: PartialFunction[(Long, WriteCommand[_]), Transaction] = {
    case (index, Put(key: String, value: Transaction)) =>
      lastIndex = index

      // If key is "cut-block", cut the current block.
      // Sequence number of the block that needs to be cut is in the amount field of the dummy transaction
      if(key.equals(Config.cutBlockMessage)) {
        orderer.receiveCutBlock(value.getAmount.toInt)
      } else {
        orderer.addTransactionToBlock(value)
      }
      value
  }

  // CKite needs to know the last applied write on log replay to
  // provide exactly-once semantics
  // If no persistence is needed then state machines can just return zero
  def getLastAppliedIndex: Long = lastIndex

  // called during Log replay on startup and upon installSnapshot requests
  def restoreSnapshot(byteBuffer: ByteBuffer): Unit = {}

  // called when Log compaction is required
  def takeSnapshot(): ByteBuffer = ByteBuffer.wrap(new Array[Byte](0))

}

// ReadCommands are not replicated but forwarded to the Leader
case class Get(key: String) extends ReadCommand[Option[String]]

// WriteCommands are replicated under Raft rules
case class Put(key: String, value: Transaction) extends WriteCommand[String]
