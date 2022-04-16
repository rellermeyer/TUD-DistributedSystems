package Messaging

import Communication.SocketData

@SerialVersionUID(1L)
class Message(val ID: (Int, Int),
              val sender: SocketData,
              val destination: Int,
              val content: MessageType,
              val timestamp: Long) extends Serializable
