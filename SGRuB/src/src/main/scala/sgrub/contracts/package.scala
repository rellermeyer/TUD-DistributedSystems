package sgrub

import scorex.crypto.hash.{Digest32, Keccak256}

// Using Keccak256, since that's what Solidity offers by default
package object contracts {
  def KeyLength: Int = 8
  def DigestLength: Int = 33
  type DigestType = Digest32
  type HashFunction = Keccak256.type
  def hf: HashFunction = Keccak256
}
