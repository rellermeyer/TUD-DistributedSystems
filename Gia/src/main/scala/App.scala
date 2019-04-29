package gia.core

import akka.actor.{ActorSystem}
import com.typesafe.config.ConfigFactory

object App {

  def main(args : Array[String]) {
    val initIp = args.lift(0).getOrElse("first")
    val initPort = args.lift(1).getOrElse("0").toInt
    val ownPort = args.lift(2).getOrElse("6346").toInt

    val config = ConfigFactory.parseString(
      """akka {
        actor {
          provider = "akka.remote.RemoteActorRefProvider"
          warn-about-java-serializer-usage = false
        }
        remote {
          enabled-transports = ["akka.remote.netty.tcp"]
          netty.tcp {
            hostname = "localhost"
            port = """" + ownPort.toString + """"
          }
        }
      }"""
    )

    val system = ActorSystem("gia", ConfigFactory.load(config))
    system.actorOf(GiaActor.props(initIp, initPort, 10), "giaActor")

  }

}