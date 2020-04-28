package dynamodb.node

import java.security.MessageDigest

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import dynamodb.node.ring.{RingNode, Ring}

import scala.collection.immutable.LinearSeq

object DistributedHashTable {

  sealed trait Response

  final case object OK extends Response


  sealed trait Command

  final case class AddNode(ringNode: RingNode, replyTo: ActorRef[Response]) extends Command

  final case class ResetRing(replyTo: ActorRef[Response]) extends Command

  final case class GetRing(replyTo: ActorRef[Ring]) extends Command

  final case class GetTopN(hash: BigInt, n: Int, replyTo: ActorRef[Option[Ring]]) extends Command

  implicit val order: Ordering[RingNode] = Ordering.by(node => node.position)

  /**
   * Be careful, the ring is infinite
   *
   * @param ring An ordered (by position) stream which loops around itself
   * @param size The size of the ring
   * @return self
   */
  def apply(ring: Ring = LazyList.empty, size: Int = 0): Behavior[Command] = Behaviors.receiveMessage {
    case AddNode(ringNode, replyTo) =>
      replyTo ! OK
      DistributedHashTable(createRing((ringNode +: ring.take(size)).sorted), size + 1)
    case ResetRing(replyTo) =>
      replyTo ! OK
      DistributedHashTable()
    case GetRing(replyTo) =>
      replyTo ! ring.take(size)
      Behaviors.same
    case GetTopN(hash, _, replyTo) if ring.isEmpty || ring(size -1).position <= hash =>
      replyTo ! None
      Behaviors.same
    case GetTopN(hash, n, replyTo) =>
      replyTo ! Some(ring.dropWhile(r => r.position <= hash).take(n))
      Behaviors.same
  }

  def createRing[A](values: LinearSeq[A]): LazyList[A] = {
    lazy val ring: LazyList[A] = LazyList.from(values) #::: ring
    ring
  }

  // Get the MD5 hash of a key as a BigInt
  def getHash(key: String): BigInt = {
    val hash = MessageDigest.getInstance("MD5").digest(key.getBytes)
    // We need to convert it to a hex string in order to make it unsigned
    val hexString = DistributedHashTable.convertBytesToHex(hash)
    // Radix 16 because we use hex
    BigInt(hexString, 16) % 100
  }

  // Helper method that converts bytes to a hex string
  def convertBytesToHex(bytes: Seq[Byte]): String = {
    val sb = new StringBuilder
    for (b <- bytes) {
      sb.append(String.format("%02x", Byte.box(b)))
    }
    sb.toString
  }
}
