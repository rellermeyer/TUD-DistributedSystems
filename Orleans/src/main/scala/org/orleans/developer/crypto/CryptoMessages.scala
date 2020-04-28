package org.orleans.developer.crypto

import org.orleans.silo.control.GrainPacket

object CryptoMessages {

  case class FindMessage(startValue: Int, endValue: Int, hash: String) extends GrainPacket
  case class MessageFound(value: Int, hash: String) extends GrainPacket
  case class MessageNotFound(hash: String) extends GrainPacket

}
