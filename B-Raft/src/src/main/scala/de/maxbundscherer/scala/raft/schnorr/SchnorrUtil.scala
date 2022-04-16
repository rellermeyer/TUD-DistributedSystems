package de.maxbundscherer.scala.raft.schnorr

import de.maxbundscherer.scala.raft.schnorr.Schnorr._
import scala.io.Source
import scala.util.Random

case class Point(x: BigInt, y: BigInt)

  object SchnorrUtil {
    /**
     * Runs the Schnorr sign and verification functions for some test vectors.
     */
    def test_vectors(): Boolean = {
      println("Secret Key, Public Key, Auxiliary, Message, Signature, Verification")
      val bufferedSource = Source.fromFile(getClass.getClassLoader.getResource("vectors.csv").getPath.replace("%20", " "))
      for (line ← bufferedSource.getLines.drop(1)) {
        val cols = line.split(",").map(_.trim)
        // index secret_key	public_key	aux_rand	message	signature	verification result	comment
        println(s"> SEC ${cols(1)}\n> PUB ${cols(2)}\n> AUX ${cols(3)}\n> MSG ${cols(4)}\n> SIG ${cols(5)}")
        var verified = false
        val string = ""
        if (cols(1) == "") {
          verified = verify(hex2big(cols(2)), hex2big(cols(4)), hex2big(cols(5)))
        } else {
          verified = verify(hex2big(cols(2)), hex2big(cols(4)), hex2big(cols(5)))
        }
        println(s"Expected ${cols(6).toLowerCase()} and got $verified $string \n")
      }
      bufferedSource.close
      var (count, n) = (0, 10)
      for (_ ← count to n) {
        val msg = r_big()
        val str = Random.nextString(32)
        val (sk, pk) = generateKeypair()
        if (!verify(pk, msg, sign(sk, msg))) count += 1
        if (!string_verify(pk, str, string_sign(sk, str))) count += 1
      }
      println("Successful for " + (2 * n - count) + "/" + (2 * n) + " randomised vector sets")
      true
    }

    /**
     * Generate a random with system time
     * @return the random object
     */
    def get_seed(): Random = {
      new scala.util.Random(new java.util.Date().hashCode)
    }

    /**
     * Generate a random big integer using system time
     * @return the random big integer
     */
    def r_big(): BigInt = {
      BigInt.probablePrime(bitLength = 32, get_seed())
    }

    /**
     * Convert a hex string to a big integer
     * @param hex as the hex string
     * @return the big integer
     */
    def hex2big(hex: String): BigInt = {
      BigInt(("00" + hex).sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte))
    }

    /**
     * Convert a big integer to a hex string
     * @param x as the big integer
     * @param size as the byte length
     * @return
     */
    def big2hex(x: BigInt, size: Int = 32): String = {
      "0x" + big2bytes(x, size).map("%02x" format _).mkString
    }

    /**
     * Convert a big integer to an unsigned byte array
     * @param x as the big integer
     * @param size as the byte length
     * @return
     */
    def big2bytes(x: BigInt, size: Int = 32): Array[Byte] = {
      val bytes = x.toByteArray
      if (bytes.length == size) x.toByteArray
      else if (bytes.length > size) bytes.slice(bytes.length - size, bytes.length)
      else {
        val buffer = new Array[Byte](size)
        Array.copy(bytes, 0, buffer, buffer.length - bytes.length, bytes.length)
        buffer
      }
    }

    /**
     * Convert some big integer to an unsigned big integer
     * @param x as the signed big integer
     * @return the unsigned big integer
     */
    def bytes2int(x: Array[Byte]): BigInt = {
      BigInt(1, x)
    }

    /**
     * Concatenate the unsigned byte arrays of two big integers
     * @param x1 the first big integer
     * @param x2 the second big integer
     * @return
     */
    def concat(x1: BigInt, x2: BigInt): Array[Byte] = {
      big2bytes(x1) ++ big2bytes(x2)
    }

    /**
     * Concatenate the unsigned byte arrays of three big integers
     * @param x1 the first big integer
     * @param x2 the second big integer
     * @param x3 the third big integer
     * @return
     */
    def concat(x1: BigInt, x2: BigInt, x3: BigInt): Array[Byte] = {
      big2bytes(x1) ++ big2bytes(x2) ++ big2bytes(x3)
    }
  }
