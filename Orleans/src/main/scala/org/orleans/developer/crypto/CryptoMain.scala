package org.orleans.client


import java.security.MessageDigest

import com.typesafe.scalalogging.LazyLogging
import org.orleans.developer.crypto.{Hasher, HasherRef}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Random, Success}

object CryptoMain extends LazyLogging{

  def getHash(message: Int): String = {
    MessageDigest.getInstance("SHA-256")
      .digest(BigInt(message).toByteArray)
      .map("%02x".format(_)).mkString
  }

  def main(args: Array[String]): Unit = {

    val runtime = OrleansRuntime()
      .registerGrain[Hasher]()
      .setHost("localhost")
      .setPort(1400)
      .build()

    val maxInt = 10000

    val message = new Random().nextInt(maxInt)
    logger.info(f"Message is $message")

    val hash = getHash(message)

    println(hash)

    var hasherFutures: List[Future[HasherRef]] = List()
    for (i <- 0 until 5) {
      hasherFutures = hasherFutures :+ runtime.createGrain[Hasher, HasherRef]()
    }

    val hashersFuture: Future[List[HasherRef]] = for (x <- Future.sequence(hasherFutures)) yield x

    val hasherRefs: List[HasherRef] = Await.ready(hashersFuture, 20 seconds).value.get match {
      case Success(value) => value
      case Failure(exception) =>
        logger.error(f"could not get hasherRefs: $exception")
        return
    }

    var messageFound = false

    for (i <- hasherRefs.indices) {
      val numberOfHashes: Int = maxInt / hasherRefs.length
      hasherRefs(i).findHash(numberOfHashes * i, numberOfHashes * (i + 1), hash).onComplete {
        case Success(Some(value)) =>
          logger.info(f"Hasher $i has found the original message; $value")
          messageFound = true
        case _ =>
      }
    }

    while(!messageFound) {
      logger.info("Message not found yet, sleeping...")
      Thread.sleep(500)
    }

  }
}
