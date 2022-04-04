package peer

import akka.actor.typed.ActorRef
import logic.wall.File

trait PeerMessage

/**
 * PeerMessage
 */

// used for login REPL command
case class Login(location: String, path: String) extends PeerMessage

// used for logout REPL command
case class Logout(location: String) extends PeerMessage

case class Message(sender: String, text: String, ack: Boolean, id : Long = -1L) extends PeerMessage

case class AddWallEntry(sender:String,text: String) extends PeerMessage

// for now assume version == 0
case class FileRequest(fileName: String, version: Int, replyTo: ActorRef[PeerMessage],id : Long = -1L) extends PeerMessage

/**
 * @param code response code. Kinda like http response codes.
 */
case class FileResponse(code: Int, fileName: String, version: Int,
                        file: Option[File], from: ActorRef[PeerMessage],id : Long = -1L) extends PeerMessage

/**
 * for sending commands to the peer
 * @param cmd of Command trait
 */
case class PeerCmd(cmd: Command) extends PeerMessage

/**
 * offline notifications
 * @param content Any, e.g., AsyncMessage.OfflineMessage, etc.
 */
case class Notification(content: Any) extends PeerMessage




