import akka.util.Timeout

import scala.concurrent.duration._

object ConnectionConfig {
    val port = 2552
    val managerPort = 2551
    val timeout: Timeout = 3.seconds
    val heartbeatInterval: FiniteDuration = 10.seconds
    val wrapperFailureInterval: FiniteDuration = 59.seconds
}
