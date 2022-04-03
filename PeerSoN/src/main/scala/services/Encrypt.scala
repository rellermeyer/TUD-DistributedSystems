package services

import java.math.BigInteger
import java.security.MessageDigest

object Encrypt {
  /**
   * encrypt the received string with SHA-256
   * Used SHA-256 because MD5 used in paper is not secure
   * (Checked with linux: "echo -n "Test@test.com" | openssl dgst -sha256")
   * reference:
   * https://stackoverflow.com/questions/46329956/how-to-correctly-generate-sha-256-checksum-for-a-string-in-scala
   *
   * @param value the string (usually the email) to hash
   * @return the encrypted result - of type String
   */
  def apply(value: String): String = {
    val digest = MessageDigest
      .getInstance("SHA-256")
      .digest(value.getBytes("UTF-8"))
    String.format("%064x", new BigInteger(1, digest))
  }
}
