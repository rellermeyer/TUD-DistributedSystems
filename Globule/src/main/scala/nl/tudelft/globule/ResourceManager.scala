package nl.tudelft.globule

import java.io._

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}
import nl.tudelft.globule.NetworkManager.RemoteAddress
import nl.tudelft.globule.ReplicationManager.FileReplicated


final case class ReceiveFile(fileDescription: FileDescription, content: Array[Byte], contentLength: Int)

final case class TransferFileToRemote(fileDescription: FileDescription, remoteResourceManager: ActorRef)

final case class AckTransferFile(fileDescription: FileDescription, remoteAddress: RemoteAddress)

final case class RejectFileTransfer(reason: String)

final case class AddRemoteAddress(address: String)

final case class RemoveRemoteAddress(address: String)

object ResourceManagerApi {
  def props(resourceManager: ActorRef) = Props(new ResourceManagerApi(resourceManager))
}

class ResourceManagerApi(resourceManager: ActorRef) extends Actor {
  override def receive: Receive = {
    case ReceiveFile(fileDescription: FileDescription, content: Array[Byte], contentLength: Int) =>
      resourceManager forward ReceiveFile(fileDescription: FileDescription, content: Array[Byte], contentLength: Int)
  }
}

object ResourceManager {
  def props(replicationManagerRef: Option[ActorSelection], remoteAddress: RemoteAddress) = Props(new ResourceManager(replicationManagerRef, remoteAddress))
}

class ResourceManager(replicationManagerRef: Option[ActorSelection], serverAddress: RemoteAddress) extends Actor with ActorLogging {
  val replicaData = collection.mutable.Map[String, String]()

  def allowedToReplicate(description: FileDescription): Boolean = {
    replicaData.contains(description.servername)
  }

  def addRemote(serverName: String) = {
    replicaData.put(serverName, "")
  }

  def removeRemote(serverName: String) = {
    replicaData.remove(serverName)
  }

  def writeContentToFile(filePath: String, content: Array[Byte], length: Int) = {

    val filePathChunks = filePath.split("/")
    val filePathWithoutFilename = filePathChunks.dropRight(1).mkString("/")
    val file = new File(filePathWithoutFilename)
    file.mkdirs()
    // Create outputstream to write the new filecontent to
    val out = new BufferedOutputStream(new FileOutputStream(filePath))
    out.write(content, 0, length)
    // Close the file to make sure we flush the buffer
    out.close()
  }

  override def receive: Receive = {
    case AddRemoteAddress(serverName: String) =>
      addRemote(serverName)
    case RemoveRemoteAddress(serverName: String) =>
      removeRemote(serverName)
    case ReceiveFile(description: FileDescription, content, length) =>
      log.info(s"ResourceManager => Receiving  ${description.filename} file to replicate")
      writeContentToFile(description.filePath, content, length)
      // Send acknnowledgement
      log.info(s"ResourceManager => File ${description.filename} replication finished")
      sender ! AckTransferFile(description, serverAddress)

    case TransferFileToRemote(fileDescription: FileDescription, remoteResourceManager: ActorRef) =>
      // Locate file
      log.info(s"ResourceManager => Replicating file ${fileDescription.filename} to $remoteResourceManager")
      val file = new File(fileDescription.filePath)

      val input = new BufferedInputStream(new FileInputStream(file))

      if (file.length() > Int.MaxValue) {
        throw new Exception("File length is larger than Int datatype!")
      }

      val bytes = new Array[Byte](file.length().toInt)

      // Read file and stream to remote Resource Manager
      Stream
        .continually(input.read(bytes))
        .takeWhile(-1 != _)
        .foreach(read => remoteResourceManager ! ReceiveFile(fileDescription, bytes, read))
    case AckTransferFile(description, remoteAddress) =>
      replicationManagerRef.get ! FileReplicated(description, remoteAddress)
  }
}