package org.orleans.developer.crypto

import java.security.MessageDigest

import org.orleans.developer.crypto.CryptoMessages.{FindMessage, MessageFound, MessageNotFound}
import org.orleans.silo.dispatcher.Sender
import org.orleans.silo.services.grain.Grain
import org.orleans.silo.services.grain.Grain.Receive

class Hasher(id: String) extends Grain(id){
  override def receive: Receive = {
    case (findMessage: FindMessage, sender: Sender) => processFindMesage(findMessage) match {
      case Some(value) => sender ! MessageFound(value, findMessage.hash)
      case None => sender ! MessageNotFound(findMessage.hash)
    }
  }

  def getHash(message: Int): String = {
    MessageDigest.getInstance("SHA-256")
      .digest(BigInt(message).toByteArray)
      .map("%02x".format(_)).mkString
  }

  def processFindMesage(findMessage: FindMessage): Option[Int] = {
    for(i <- findMessage.startValue until findMessage.endValue) {
      if (getHash(i) == findMessage.hash) {
        println(s"Found message: $i")
        return Some(i)
      }
    }
    None
  }
}
