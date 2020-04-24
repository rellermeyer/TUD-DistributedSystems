package initializers

import actors.MasterService.InitMasterService
import actors.MasterService
import akka.NotUsed
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import com.typesafe.config.ConfigFactory

object MasterServiceInitializer {

    def apply(): Behavior[NotUsed] =
        Behaviors.setup {
            context => {
                val masterService = context.spawn(MasterService(), "CRS")
                masterService ! InitMasterService()

                Behaviors.receiveSignal {
                    case (_, Terminated(_)) =>
                        context.log.info("Stopping the system.")
                        Behaviors.stopped
                }
            }
        }

    def main(args: Array[String]): Unit = {
        val config = ConfigFactory.load()
        ActorSystem(MasterServiceInitializer(), "CRS", config.getConfig("masterService").withFallback(config))
    }

}
