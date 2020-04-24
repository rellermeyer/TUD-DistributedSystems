package org.orleans
import java.security.MessageDigest
import java.util

import ch.qos.logback.classic.Level
import org.orleans.client.OrleansRuntime
import org.orleans.developer.crypto.{Hasher, HasherRef}
import org.orleans.developer.twitter.{Twitter, TwitterAccount, TwitterAcountRef, TwitterRef}
import org.orleans.silo.storage.GrainDatabase
import org.orleans.silo.{Master, Slave}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Success}

object Main {

  def main(args: Array[String]): Unit = {
    GrainDatabase.disableDatabase = true
    setLevel(Level.INFO) // The debug level might give a little bit too much info.
    args.toList match {
      case level :: "master" :: tail if isInt(level) => {
        if (level.toInt == 1)
          setLevel(Level.DEBUG)
        processMasterArgs(tail)
      }
      case level :: "slave" :: tail if isInt(level) => {
        if (level.toInt == 1)
          setLevel(Level.DEBUG)
        processSlaveArgs(tail)
      }
      case level :: "client" :: tail if isInt(level) => {
        if (level.toInt == 1)
          setLevel(Level.DEBUG)
        processClientArgs(tail)
      }
      case _ =>
        new RuntimeException(
          "Unknown command, please specify if you want to start a master, client or slave."
        )
    }
  }

  //MASTERHOST TCPPORT UDPPORT GRAINPORTSTART GRAINPORTEND
  //Example: 1400 1500 1501 1510
  def processMasterArgs(args: List[String]) = args match {
    case tcpPort :: udpPort :: grainPortStart :: graintPortEnd :: Nil
        if isInt(tcpPort) &&
          isInt(udpPort) &&
          isInt(grainPortStart) &&
          isInt(graintPortEnd) =>
      runMaster(
        sys.env("HOSTNAME") + ".orleans",
        tcpPort.toInt,
        udpPort.toInt,
        grainPortStart.toInt,
        graintPortEnd.toInt
      )
    case _ =>
      throw new RuntimeException(
        "Unknown command, run as: 'master TCPPORT UDPPORT GRAINPORTSTART GRAINPORTEND'"
      )
  }

  //TCPPORT UDPPORT GRAINPORTSTART GRAINPORTEND MASTERHOST MASTERTCP MASTERUDP
  //Example: localhost 1400 1500 1501 1510 master-host 1400 1500
  def processSlaveArgs(args: List[String]) = args match {
    case tcpPort :: udpPort :: grainPortStart :: graintPortEnd :: masterHost :: masterTCP :: masterUDP :: Nil
        if isInt(tcpPort) &&
          isInt(udpPort) &&
          isInt(grainPortStart) &&
          isInt(graintPortEnd) &&
          isInt(masterTCP) &&
          isInt(masterUDP) =>
      runSlave(
        sys.env("HOSTNAME") + ".slave-headless.orleans",
        tcpPort.toInt,
        udpPort.toInt,
        grainPortStart.toInt,
        graintPortEnd.toInt,
        masterHost,
        masterTCP.toInt,
        masterUDP.toInt
      )
    case _ =>
      throw new RuntimeException(
        "Unknown command, run as: 'slave TCPPORT UDPPORT GRAINPORTSTART GRAINPORTEND MASTERHOST MASTERTCP MASTERUDP'"
      )
  }

  def processClientArgs(args: List[String]) = args match {
    case scenarioId :: masterHost :: masterTCP :: Nil
        if isInt(scenarioId) && isInt(masterTCP) =>
      runClientScenario(scenarioId.toInt, masterHost, masterTCP.toInt)
    case _ =>
      throw new RuntimeException(
        "Unknown command, run as: 'client SCENARIOID MASTERHOST MASTERTCP"
      )
  }

  def runClientScenario(id: Int, masterHost: String, masterTCP: Int) = {
    println(s"Now trying to run scenario $id.")
    id match {
      case 1 => runClientScenarioOne(masterHost, masterTCP)
      case 2 => runClientScenarioTwo(masterHost, masterTCP)
      case 3 => runClientScenarioThree(masterHost, masterTCP)
      case 4 => runClientScenarioFour(masterHost, masterTCP)
      case 5 => runClientScenarioFive(masterHost, masterTCP)
      case 6 => runClientScenarioSix(masterHost, masterTCP)
      case 7 => runClientScenarioSeven(masterHost, masterTCP)
      case 8 => runClientScenarioEight(masterHost, masterTCP)
      case _ => throw new RuntimeException("Can't find client scenario.")
    }
    println(
      s"Running scenario $id finished."
    )
  }

  def runMaster(host: String,
                tcp: Int,
                udp: Int,
                grainPortStart: Int,
                graintPortEnd: Int): Unit = {
    GrainDatabase.disableDatabase = true
    Master()
      .registerGrain[Hasher]
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(host)
      .setTCPPort(tcp)
      .setUDPPort(udp)
      .setExecutionContext(ExecutionContext.global)
      .setGrainPorts((grainPortStart to graintPortEnd).toSet)
      .build()
      .start()
  }

  def runSlave(host: String,
               tcp: Int,
               udp: Int,
               grainPortStart: Int,
               graintPortEnd: Int,
               masterHost: String,
               masterTCP: Int,
               masterUDP: Int): Unit = {
    GrainDatabase.disableDatabase = true
    Slave()
      .registerGrain[Hasher]
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(host)
      .setTCPPort(tcp)
      .setUDPPort(udp)
      .setExecutionContext(ExecutionContext.global)
      .setGrainPorts((grainPortStart to graintPortEnd).toSet)
      .setMasterHost(masterHost)
      .setMasterTCPPort(masterTCP.toInt)
      .setMasterUDPPort(masterUDP.toInt)
      .build()
      .start()
  }

  def runClientScenarioOne(masterHost: String, tcpPort: Int) = {
    GrainDatabase.disableDatabase = true
    println(GrainDatabase.instance)
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    var time = System.currentTimeMillis()
    val twitterFuture: Future[TwitterRef] =
      runtime.createGrain[Twitter, TwitterRef]()
    val twitter = Await.result(twitterFuture, 5 seconds)
    println(
      s"Creating a Twitter grain took ${System.currentTimeMillis() - time}ms")
  }

  def runClientScenarioTwo(masterHost: String, tcpPort: Int) = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    var time = System.currentTimeMillis()
    val twitterFuture: Future[TwitterRef] =
      runtime.createGrain[Twitter, TwitterRef]()
    val twitter = Await.result(twitterFuture, 5 seconds)
    println(
      s"Creating a Twitter grain took ${System.currentTimeMillis() - time}ms")

    val users = 10
    println(s"Now creating $users users.")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user: TwitterAcountRef =
        Await.result(twitter.createAccount(s"wouter-${i}"), 5 seconds)
    }

    //Await.result(twitter.createAccount(s"wouter-${i}"), 1 seconds)
    println(s"That took ${System.currentTimeMillis() - time}ms")

    println(s"Now searching those $users users and show following list.")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user = Await.result(twitter.getAccount(s"wouter-${i}"), 50 seconds)
      for (j <- (1 to users)) {
        if (i != j) {
          Await.result(user.followUser(twitter, s"wouter-${j}"), 5 seconds)
        }
      }

      val followers = Await.result(user.getFollowingList(), 50 seconds)
      println(s"wouter-${i} - ${followers.size} followers")
    }

    //Await.result(twitter.createAccount(s"wouter-${i}"), 1 seconds)
    println(s"That took ${System.currentTimeMillis() - time}ms")

    println(s"Now searching those $users users and send 10 000 tweets.")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user = Await.result(twitter.getAccount(s"wouter-${i}"), 50 seconds)
      var futures: List[Future[Any]] = List()
      for (j <- (1 to 10000)) {
        user.tweet("I like dis")
      }

    }
    //Await.result(twitter.createAccount(s"wouter-${i}"), 1 seconds)
    println(s"That took ${System.currentTimeMillis() - time}ms")

    println("Waiting 5 seconds so all tweets are processed.")
    Thread.sleep(5000)
    println(s"Now searching those $users users and get amount of tweets..")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user = Await.result(twitter.getAccount(s"wouter-${i}"), 50 seconds)
      val size = Await.result(user.getAmountOfTweets(), 50 seconds)
      println(s"wouter-${i} - $size tweets")
    }
    println(s"That took ${System.currentTimeMillis() - time}ms")

    println(s"Now searching those $users users and get all tweets.")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user = Await.result(twitter.getAccount(s"wouter-${i}"), 50 seconds)
      val tweets = Await.result(user.getTweets(), 50 seconds)
      println(s"wouter-${i} - ${tweets.size} tweets")
    }
    println(s"That took ${System.currentTimeMillis() - time}ms")

    println(s"Now searching those $users users and get their timeline")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user = Await.result(twitter.getAccount(s"wouter-${i}"), 50 seconds)
      val tweets = Await.result(user.getTimeline(twitter), 50 seconds)
      println(s"wouter-${i} - ${tweets.size} tweets")
    }
    println(s"That took ${System.currentTimeMillis() - time}ms")
  }

  def runClientScenarioThree(masterHost: String, tcpPort: Int): Unit = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    var time = System.currentTimeMillis()
    val twitterFuture: Future[TwitterRef] =
      runtime.createGrain[Twitter, TwitterRef]()
    val twitter = Await.result(twitterFuture, 5 seconds)
    println(
      s"Creating a Twitter grain took ${System.currentTimeMillis() - time}ms")

    val users = 100000
    println(s"Now creating $users users.")
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user: TwitterAcountRef =
        Await.result(twitter.createAccount(s"test-${i}"), 5 seconds)
    }

    var run = true
    Future
      .sequence((1 to users).toList.map(i => twitter.createAccount(s"test-$i")))
      .onComplete {
        case Success(list) => {
          println(s"That took ${System.currentTimeMillis() - time}ms")
          run = false
        }
        case Failure(exception) => {
          println(exception)
          run = false
        }
      }

    while (run) {
      Thread.sleep(50)
    }
    //Await.result(twitter.createAccount(s"wouter-${i}"), 1 seconds)
  }

  def runClientScenarioFour(masterHost: String, tcpPort: Int): Unit = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    var time = System.currentTimeMillis()

    val users = 1000
    println(s"Now creating $users users.")
    Await.result(Future.sequence((1 to users).toList.map(_ => runtime.createGrain[TwitterAccount, TwitterAcountRef])), 100 seconds)
    println(s"That took ${System.currentTimeMillis() - time}ms")
    System.exit(0)

  }

  def runClientScenarioFive(masterHost: String, tcpPort: Int): Unit = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    var time = System.currentTimeMillis()
    val twitterFuture: Future[TwitterRef] =
      runtime.createGrain[Twitter, TwitterRef]()
    val twitter = Await.result(twitterFuture, 5 seconds)
    println(
      s"Creating a Twitter grain took ${System.currentTimeMillis() - time}ms")

    val userList: util.ArrayList[TwitterAcountRef] = new util.ArrayList[TwitterAcountRef]()
    val users = 100
    time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      val user: TwitterAcountRef =
        Await.result(twitter.createAccount(s"wouter-${i}"), 5 seconds)
      userList.add(user)
      for (i <- (1 to 10000)) {
        user.tweet("a tweet!")
      }
    }

    println(s"Created users and tweeted: it took ${System.currentTimeMillis() - time}ms")


    val hash : mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]()
        for (user <- userList.asScala.toList) {
          val tweets = Await.result(user.getTweets(), 5 seconds)

          if (hash.contains(user.grainRef.address)) {
            hash.put(user.grainRef.address, hash.get(user.grainRef.address).get + 1)
          } else {
            hash.put(user.grainRef.address, 1)
          }
        }
    hash.foreach(println(_))

    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)
    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    System.exit(0)
  }

  def runClientScenarioSix(masterHost: String, tcpPort: Int): Unit = {
    val runtime = OrleansRuntime()
      .registerGrain[Hasher]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    val hasherList: util.ArrayList[HasherRef] = new util.ArrayList[HasherRef]()
    for (i <- 1 to 500) {
      val hasher = Await.result(runtime.createGrain[Hasher, HasherRef](), 5 seconds)
      hasherList.add(hasher)
    }

    println("Created hasherlists.")


    var sumTime = 0L

    for (i <- 0 to 100) {
      val maxInt = 100000

      val message = new Random().nextInt(maxInt)

      val hash = getHash(message)

      //      println(hash)

      var messageFound = false
      val startTime = System.currentTimeMillis()
      var futures: List[Future[Option[Int]]] = List()

      for (i <- 0 until hasherList.size()) {
        val numberOfHashes: Int = maxInt / hasherList.size()
        val findHashFuture: Future[Option[Int]] = hasherList.get(i).findHash(numberOfHashes * i, numberOfHashes * (i + 1), hash)

        futures = futures :+ findHashFuture

        findHashFuture.onComplete {
          case Success(Some(value)) =>
            //            logger.debug(f"Hasher $i has found the original message; $value")
            messageFound = true
            println(System.currentTimeMillis() - startTime)
            sumTime += System.currentTimeMillis() - startTime
          case _ =>
        }
      }

      for (future <- futures) {
        Await.result(future, 20 seconds)
      }
    }

    println(s"avarage: ${sumTime/100}")
  }

  def runClientScenarioSeven(masterHost: String, tcpPort: Int): Unit = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()


    val userList: util.ArrayList[TwitterAcountRef] = new util.ArrayList[TwitterAcountRef]()
    val users = 100
    var time = System.currentTimeMillis()
    for (i <- (1 to users)) {
      println(i)
      val user: TwitterAcountRef =
        Await.result(runtime.createGrain[TwitterAccount, TwitterAcountRef], 5 seconds)
      userList.add(user)
//      for (i <- (1 to 10000)) {
//        user.tweet("a tweet!")
//      }
    }

    println(s"Created users and tweeted: it took ${System.currentTimeMillis() - time}ms")


    val hash : mutable.HashMap[String, Int] = new mutable.HashMap[String, Int]()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 60 seconds)
    for (user <- userList.asScala.toList) {
      if (hash.contains(user.grainRef.address)) {
        hash.put(user.grainRef.address, hash.get(user.grainRef.address).get + 1)
      } else {
        hash.put(user.grainRef.address, 1)
      }
    }
    hash.foreach(println(_))

    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)
    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    time = System.currentTimeMillis()
    Await.result(Future.sequence(userList.asScala.toList.map(_.getTweets())), 30 seconds)

    println(System.currentTimeMillis() - time)
    System.exit(0)
  }

  def runClientScenarioEight(masterHost: String, tcpPort: Int) = {
    GrainDatabase.disableDatabase = true
    val runtime = OrleansRuntime()
      .registerGrain[Twitter]
      .registerGrain[TwitterAccount]
      .setHost(masterHost)
      .setPort(tcpPort)
      .build()

    val userList: util.ArrayList[TwitterAcountRef] = new util.ArrayList[TwitterAcountRef]()
    val users = 15
    var time = System.currentTimeMillis()
    for (i <- 1 to users) {
      println(i)
      val user: TwitterAcountRef =
        Await.result(runtime.createGrain[TwitterAccount, TwitterAcountRef], 5 seconds)
      userList.add(user)
    }

    time = System.currentTimeMillis()
    val tweets = 100000
    val threadList: util.ArrayList[Thread] = new util.ArrayList[Thread]()
    for (user <- userList.asScala.toList) {
      println(user.grainRef.id)
      val t = new Thread(new Runnable {
        override def run(): Unit = {
          (1 to tweets).toList.foreach(x => user.tweet("hi"))
        }
      })

        t.start()
      threadList.add(t)

    }
    println("send all tweets")

    for (t <- threadList.asScala) {
      t.join()
    }


//    var notLoaded = true
//    while(notLoaded) {
//      val result: List[Int] = Await.result(Future.sequence(userList.asScala.map(_.getAmountOfTweets())), 60 seconds).toList
//      notLoaded = result.map(_ == tweets).contains(false)
//
//      Thread.sleep(10)
//    }

    val timeDiff = System.currentTimeMillis() - time
    val throughput = (tweets * users) / timeDiff

    println(throughput)
    println(s"Send ${tweets*users} tweets in ${timeDiff} ms")
    val timeSec = timeDiff / 1000
    println(s"${(tweets * users)/timeSec} messages/second")
    System.exit(0)
  }


  def getHash(message: Int): String = {
    MessageDigest.getInstance("SHA-256")
      .digest(BigInt(message).toByteArray)
      .map("%02x".format(_)).mkString
  }

  /** Very hacky way to set the log level */
  def setLevel(level: Level) = {
    val logger: ch.qos.logback.classic.Logger =
      org.slf4j.LoggerFactory
        .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        .asInstanceOf[(ch.qos.logback.classic.Logger)]
    logger.setLevel(level)
  }

  def isInt(x: String) = x forall Character.isDigit
}
