package de.maxbundscherer.scala.raft.schnorr

import java.nio.charset.Charset
import java.security.MessageDigest
import de.maxbundscherer.scala.raft.schnorr.SchnorrUtil._

object SchnorrMath {
    var P: BigInt = hex2big("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F")
    var N: BigInt = hex2big("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141")
    var X: BigInt = hex2big("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798")
    var Y: BigInt = hex2big("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8")
    var G: Point = Point(X, Y)

    /**
     * Generate a 32-byte hash with SHA256(SHA256(tag) || SHA256(tag) || msg)
     * @param tag to be used to hash with
     * @param msg to be encoded
     * @return the sha256 digest
     */
    def hashtag(tag: String, msg: Array[Byte]): BigInt = {
      val sha256 = MessageDigest.getInstance("SHA-256")
      val bytes = sha256.digest(tag.getBytes(Charset.forName("UTF-8")))
      BigInt(1, sha256.digest(bytes ++ bytes ++ msg))
    }

    /**
     * Computes the signature as a big integer
     * @param k The nonce hash
     * @param e The hashed message
     * @param d The private key
     * @return the resulting signature
     */
    def compute_sign(k: BigInt, e: BigInt, d: BigInt): BigInt = {
      val x1 = (BigInt(1, big2bytes(e)) * BigInt(1, big2bytes(d))).mod(N)
      (x1 + BigInt(1, big2bytes(k))).mod(N)
    }

    /**
     * Determine whether the point lays at infinity
     * @param p as the point to evaluate
     * @return true if at infinity
     */
    def is_infinite(p: Option[Point]): Boolean = p match {
      case None ⇒ true
      case _    ⇒ false
    }

    /**
     * Determine whether the y-coordinate is even
     * @param p as the point to evaluate
     * @return true if y is even
     */
    def even_y(p: Option[Point]): Boolean = {
      assert(!is_infinite(p))
      (p.get.y % 2) == 0
    }

    /**
     * Performs elliptic curve group operation on two points
     * @param p1 as the first point
     * @param p2 as the second point
     * @return the newly computed point
     */
    def point_add(p1: Option[Point], p2: Option[Point]): Option[Point] = {
      if (p1.isEmpty) return p2
      if (p2.isEmpty) return p1
      val (x1, x2) = (p1.get.x, p2.get.x)
      val (y1, y2) = (p1.get.y, p2.get.y)
      var lam: BigInt = 0
      if (x1 == x2) {
        if (y1 != y2) return None
        else lam = ((x1 * x1 * 3) * (y1 * 2).modPow(P - 2, P)).mod(P)
      } else {
        lam = ((y2 - y1) * (x2 - x1).modPow(P - 2, P)).mod(P)
      }
      val x3 = (lam * lam - x1 - x2).mod(P)
      Some(Point(x3, ((lam * (x1 - x3)).mod(P) - y1).mod(P)))
    }

    /**
     * Performs elliptic curve point multiplication using some scalar
     * @param p the point to multiply
     * @param n the scalar as big integer
     * @return the resulting point after repeated addition
     */
    def point_mul(p: Option[Point], n: BigInt): Option[Point] = {
      var q: Option[Point] = p
      var r: Option[Point] = None
      for (i ← 0 to 256) {
        if (((n >> i) & 1) == 1) r = point_add(r, q)
        q = point_add(q, q)
      }
      r
    }

    /**
     * Finds an even y-coordinate for some x if it exists
     * @param x the x-coordinate to consider
     * @return the point with even y if found
     */
    def lift(x: BigInt): Option[Point] = {
      if (x >= P) return None
      val sq = (x.modPow(3, P) + 7).mod(P)
      val y = sq.modPow(((P + 1) / 4).mod(P), P) // Maybe division needs flooring
      if (sq != y.modPow(2, P)) return None
      if (y.testBit(0)) Some(Point(x, P - y)) else Some(Point(x, y))
    }
  }
