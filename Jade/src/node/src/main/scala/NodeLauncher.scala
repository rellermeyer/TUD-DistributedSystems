import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
  * The launcher of a JADE hardware node.
  */
object NodeLauncher extends App {
    var system: ActorSystem = null

    if(!args.isEmpty) {
        val hostIp = args.head
        val config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + hostIp)
            .withFallback(ConfigFactory.load("application"))
        system = ActorSystem("Node", config)
    } else {
        system = ActorSystem("Node")
    }

    println("Node launcher started")

    val node = system.actorOf(Props[Node], "Node")
}
