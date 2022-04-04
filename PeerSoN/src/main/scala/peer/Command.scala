package peer

import akka.actor.typed.ActorRef

trait Command

/**
 * Command
 */

case class GetFileCommand(fileName: String, replyTo: ActorRef[PeerMessage]) extends Command
case class AddToWallCommand(user: String, text: String) extends Command
case class SendMessageCommand(receiver: String, text: String) extends Command
case class AddOfflineMessage(receiver: String, text: String, ack: Boolean) extends Command
