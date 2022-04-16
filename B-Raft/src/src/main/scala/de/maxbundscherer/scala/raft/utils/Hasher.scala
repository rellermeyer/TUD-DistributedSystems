package de.maxbundscherer.scala.raft.utils

import de.maxbundscherer.scala.raft.schnorr.SchnorrUtil.hex2big
import org.slf4j.{Logger, LoggerFactory}

import java.nio.charset.Charset
import java.security.MessageDigest

class Hasher {
  val UTF_8: Charset = Charset.forName("UTF-8")
  val logger: Logger = LoggerFactory.getLogger(this.toString)

  def hash(toHash: String): BigInt = {
    val messageDigest: MessageDigest = MessageDigest.getInstance("SHA-256")
    val hex: String = messageDigest.digest(toHash.getBytes(UTF_8)).map("%02x".format(_)).mkString
    val res = hex2big(hex)
//    logger.debug(s"hasher Hashing '$toHash', result: $res")
    res
  }
}

object Hasher {
  def apply(): Hasher = {
    new Hasher()
  }
}