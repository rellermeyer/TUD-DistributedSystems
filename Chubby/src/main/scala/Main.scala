import chubby.client.MainClient
import chubby.replica.MainServer

object Main {
  def main(args: Array[String]): Unit = {
    val clientOrServer = args(0)

    clientOrServer match {
      case "client" => MainClient.run()
      case "server" => MainServer.run()
      case _ => {
        println("Invalid option, please provide as first argument either 'client' or 'server'")

        System.exit(1)
      }
    }
  }
}
