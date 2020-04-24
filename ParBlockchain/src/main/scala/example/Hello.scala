package example

import ckite._
import ckite.CKite
import ckite.rpc._
import scala.concurrent._
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import scala.concurrent.duration.Duration

object Hello extends Greeting with App {
  println(greeting)
  val ckite: CKite = CKiteBuilder().listenAddress("localhost:9091").rpc(FinagleThriftRpc) //Finagle based transport
                          .members(Seq("localhost:9092"))
                          .stateMachine(new KVStore()) //KVStore is an implementation of the StateMachine trait
                          .bootstrap(true) //bootstraps a new cluster. only needed just the first time for the very first node
                          .build
  val ckite2: CKite = CKiteBuilder().listenAddress("localhost:9092").rpc(FinagleThriftRpc) //Finagle based transport
                          .members(Seq("localhost:9091"))
                          .stateMachine(new KVStore()) //KVStore is an implementation of the StateMachine trait
                          .build
  ckite.start()
  ckite2.start()
  val writeRes: Try[String] = Await.ready(ckite.write(Put("key1","test")), Duration.Inf).value.get

  val resultEither: Unit = writeRes match {
    case Success(t) => println(t)
    case Failure(e) => println(e)
  }

  val readRes: Option[String] = Await.ready(ckite.read(Get("key1")), Duration.Inf).value.get.get
  val resultRead: Unit = readRes match {
    case Some(t) => println(t)
    case None => println("RIP")
  }

  ckite2.stop()
  ckite.stop()
}

trait Greeting {
  lazy val greeting: String = "hello"
}
