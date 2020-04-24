package initializers

import actors.Server
import actors.Server.InitServer
import akka.NotUsed
import akka.actor.typed.{Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors

object ServerInitializerObject {

    final val MASTER_SERVICE_PATH: String = "akka://CRS@127.0.0.1:3000/user/CRS"

    def apply(): Behavior[NotUsed] =
        Behaviors.setup {
            context => {
                // Add multiple Server actors at once
                val server = context.spawn(Server(), "CRS")
                server ! InitServer(MASTER_SERVICE_PATH)

                Behaviors.receiveSignal {
                    case (_, Terminated(_)) =>
                        context.log.info("Stopping the system.")
                        Behaviors.stopped
                }
            }
        }

}
