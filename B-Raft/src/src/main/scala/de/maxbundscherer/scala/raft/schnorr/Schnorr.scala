package de.maxbundscherer.scala.raft.schnorr

import de.maxbundscherer.scala.raft.schnorr.SchnorrMath._
import de.maxbundscherer.scala.raft.schnorr.SchnorrUtil._
import de.maxbundscherer.scala.raft.utils.Hasher
import org.slf4j.{Logger, LoggerFactory}

import scala.util.Random

object Schnorr {
  def main(args: Array[String]): Unit = {
    test_vectors()
  }

  private val hasher: Hasher = Hasher()
  val logger: Logger = LoggerFactory.getLogger(this.toString)

  /**
   * Generates a public key and private key
   * @return the generated keys as pair (sk, pk)
   */
  def generateKeypair(): (BigInt, BigInt) = {
    val sk = BigInt.probablePrime(bitLength = 32*8, Random)
    (sk, generatePublicKey(sk))
  }

  /**
   * Generates a public key using a private key
   * @param sk as the 32-byte private key
   * @return the newly computed 32-byte public key
   */
  def generatePublicKey(sk: BigInt): BigInt = {
    if (sk == 0 || sk >= N) return null
    val p = point_mul(Some(G), sk)
    assert(p.isDefined)
    p.get.x
  }

  /**
   * Sign some big integer using a private key and auxiliary data
   * @param sk as the 32-byte private key
   * @param msg as the 32-byte big integer message
   * @param aux as the 32-byte random auxiliary data
   * @return the newly computed 64-byte signature as big integer
   */
  def sign(sk: BigInt, msg: BigInt, aux: BigInt = r_big()): BigInt = {
    if (sk <= 0 || sk >= N) return null
    val p: Point = point_mul(Some(G), sk).get
    val d = if (even_y(Some(p))) sk else N - sk
    val t = d ^ hashtag("BIP0340/aux", big2bytes(aux))
    val k_0 = hashtag("BIP0340/nonce", concat(t, p.x, msg)).mod(N)
    if (k_0 <= 0) return null
    val r = point_mul(Some(G), k_0).get
    val k = if (even_y(Some(r))) k_0 else N - k_0
    val e = hashtag("BIP0340/challenge", concat(r.x, p.x, msg)).mod(N)
    val signature = BigInt(1, concat(r.x, compute_sign(k, e, d).mod(N)))
    if (!verify(p.x, msg, signature)) return null
    signature
  }

  /**
   * Verify some big integer using a public key and a signature
   * @param pk as the 32-byte public key
   * @param msg as the 32-byte big integer message
   * @param sig as the 64-byte signature to use
   * @return true if the verification passed
   */
  def verify(pk: BigInt, msg: BigInt, sig: BigInt): Boolean = {
    val p = lift(pk)
    val s_l = bytes2int(big2bytes(sig, 64).slice(0, 32))
    val s_r = bytes2int(big2bytes(sig, 64).slice(32, 64))
    if (p.isEmpty || s_l - P > 0 || s_r - N > 0) return false
    val e = hashtag("BIP0340/challenge", concat(s_l, p.get.x, msg)).mod(N)
    val r = point_add(point_mul(Some(G), s_r.mod(N)), point_mul(p, N - e.mod(N)))
    if (is_infinite(r) || !even_y(r) || r.get.x - s_l != 0) return false
    true
  }

  /**
   * Sign some string using a private key and auxiliary data
   * @param sk as the 32-byte private key
   * @param str as the message string with up to 32 chars
   * @param aux as the 32-byte random auxiliary data
   * @return the newly computed 64-byte signature as big integer
   */
  implicit def string_sign(sk: BigInt, str: String, aux: BigInt = r_big()): BigInt = {
    sign(sk, hasher.hash(str), aux)
  }

  /**
   * Verify some string using a public key and a signature
   * @param pk as the 32-byte public key
   * @param str as the 32-byte big integer message
   * @param sig as the 64-byte signature to use
   * @return true if the verification passed
   */
  implicit def string_verify(pk: BigInt, str: String, sig: BigInt): Boolean = {
    val hash = hasher.hash(str)
    val res = verify(pk, hash, sig)
//    logger.debug(s"String verify '$str' res: $res, hash:$hash, pubkey: $pk")
    res
  }
}
