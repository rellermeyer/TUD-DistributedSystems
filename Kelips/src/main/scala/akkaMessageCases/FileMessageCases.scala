package akkaMessageCases

import akka.actor.ActorRef

object FileMessageCases {
  case class InsertFiletupleCall(fileName: String, replicationFactor: Int = 1)  // Call this
  case class LookupFiletupleCall(fileName: String)
  case class RequestFile(originator: ActorRef, fileName: String)
  case class SendFile(sender: ActorRef, fileName: String)
  case class InsertRequest(actor: ActorRef, fileName: String, replicationFactor: Int)
  case class FileContactReply(actor: ActorRef, fileName: String)
  case class InsertFiletuple(actor: ActorRef, fileName: String)
  case class ReturnFileUpload(fileName: String)
  case class ReturnFileLookup(fileName: String)
}
