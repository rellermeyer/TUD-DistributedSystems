package gia.core

import java.util.UUID

class ProcessedMessageBuffer {

  var index : Int = 0
  var messages : Array[UUID] = new Array[UUID](20)

  def addMessage(messageID : UUID) : Unit = {
    messages(index) = messageID

    // Reset index to 0 if the size limit is exceeded.
    if(index >= 19) {
      index = 0
    } else {
      index += 1
    }
  }

  def containsMessage(messageID : UUID) : Boolean = {
    messages.contains(messageID)
  }
}
