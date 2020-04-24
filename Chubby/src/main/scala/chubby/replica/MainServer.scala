package chubby.replica

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object MainServer {
  def run(): Unit = {
    val conf = ConfigFactory.load()
    val system = ActorSystem("server", conf.getConfig("akka.server").withFallback(conf))

    val server = new ServerGRPC(system).run()
  }
}
