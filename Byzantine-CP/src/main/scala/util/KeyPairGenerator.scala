package util

import java.security.KeyPairGenerator

import util.Messages.KeyTuple

object KeyPairGenerator {
  def apply() = {
    val kpg: KeyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    val masterKey = kpg.generateKeyPair
    val myKPG = new {
      def apply(): KeyTuple = {
        val keyPair = kpg.generateKeyPair
        val s: java.security.Signature = java.security.Signature.getInstance("SHA512withRSA")
        s.initSign(masterKey.getPrivate)
        s.update(BigInt(keyPair.getPublic.hashCode()).toByteArray)
        (keyPair.getPrivate, (keyPair.getPublic, s.sign()))
      }
    }
    (masterKey.getPublic, myKPG)
  }
}
