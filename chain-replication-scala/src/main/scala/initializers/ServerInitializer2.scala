package initializers

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

object ServerInitializer2 {

    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()
        ActorSystem(ServerInitializerObject(), "CRS", config.getConfig("server2").withFallback(config))
    }

}