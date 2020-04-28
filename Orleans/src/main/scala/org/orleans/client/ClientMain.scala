package org.orleans.client
import org.orleans.developer.twitter.{Twitter, TwitterAccount, TwitterAcountRef, TwitterRef}
import org.orleans.silo.storage.GrainDatabase

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object ClientMain {

  def main(args: Array[String]): Unit = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost("localhost")
      .setPort(1400)
      .build()

    var time = System.currentTimeMillis()
    val twitterFuture: Future[TwitterRef] =
      runtime.createGrain[Twitter, TwitterRef]()
    val twitter = Await.result(twitterFuture, 5 seconds)
    println(
      s"Creating a Twitter grain took ${System.currentTimeMillis() - time}ms")

    val users = 50
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user: TwitterAcountRef =
        Await.result(twitter.createAccount(s"wouter-${i}"), 5 seconds)

      for (i <- (1 to 1000)) {
        user.tweet("a tweet!")
      }
    }

    println(s"Created users and tweeted: it took ${System.currentTimeMillis() - time}ms")
    time = System.currentTimeMillis()
    val hash: mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]()
    for (i <- (1 to users)) {
      val user: TwitterAcountRef =
        Await.result(twitter.getAccount(s"wouter-${i}"), 5 seconds)
      val tweets = Await.result(user.getTweets(), 5 seconds)

      if (hash.contains(user.grainRef.address)) {
        hash.put(user.grainRef.address, hash.get(user.grainRef.address).get + 1)
      } else {
        hash.put(user.grainRef.address, 1)
      }
    }

    hash.foreach(println(_))
    println(System.currentTimeMillis() - time)
    System.exit(0)

  }
}
