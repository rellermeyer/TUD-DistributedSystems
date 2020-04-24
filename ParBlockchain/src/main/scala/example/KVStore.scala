package example

import ckite.statemachine._
import java.nio.ByteBuffer
import ckite.rpc.{WriteCommand, ReadCommand}
import scala.collection.mutable.Map
import ckite.util.Serializer

//KVStore is an in-memory distributed Map allowing Puts and Gets operations
class KVStore extends StateMachine {

  private var map = Map[String, String]()
  private var lastIndex: Long = 0

  //Called when a consensus has been reached for a WriteCommand
  //index associated to the write is provided to implement your own persistent semantics
  //see lastAppliedIndex
  def applyWrite: PartialFunction[(Long, WriteCommand[_]), String] = {
    case (index, Put(key: String, value: String)) => {
      System.out.println("apply write");
      map.put(key, value)
      lastIndex = index
      value
    }
  }

  //called when a read command has been received
  def applyRead: PartialFunction[ReadCommand[_], Option[String]] = {
    case Get(key) => map.get(key)
  }

  //CKite needs to know the last applied write on log replay to
  //provide exactly-once semantics
  //If no persistence is needed then state machines can just return zero
  def getLastAppliedIndex: Long = lastIndex

  //called during Log replay on startup and upon installSnapshot requests
  def restoreSnapshot(byteBuffer: ByteBuffer): Unit = {
    map = Serializer.deserialize[Map[String, String]](byteBuffer.array())
  }
  //called when Log compaction is required
  def takeSnapshot(): ByteBuffer = ByteBuffer.wrap(Serializer.serialize(map))

}

//WriteCommands are replicated under Raft rules
case class Put(key: String, value: String) extends WriteCommand[String]

//ReadCommands are not replicated but forwarded to the Leader
case class Get(key: String) extends ReadCommand[Option[String]]
