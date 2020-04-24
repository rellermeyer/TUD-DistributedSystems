package initializers

import akka.actor.typed.ActorSystem
import com.typesafe.config.ConfigFactory

object ServerInitializer3 {

    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()
        ActorSystem(ServerInitializerObject(), "CRS", config.getConfig("server3").withFallback(config))
    }

}