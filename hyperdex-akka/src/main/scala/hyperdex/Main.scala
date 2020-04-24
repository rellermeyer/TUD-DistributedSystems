package hyperdex

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory

object Main {

  sealed trait Role
  case class GatewayNodeRole(numDataNodes: Int) extends Role
  case object DataNodeRole extends Role

  def main(args: Array[String]): Unit = {
    require(args.length >= 2, "Usage: role port")
    val port = args(1).toInt
    args(0) match {
      case "data" =>
        startup(DataNodeRole, port)
      case "gateway" =>
        require(args.length == 3, "Usage: role port num_datanodes")
        val numDataNodes = args(2).toInt
        startup(GatewayNodeRole(numDataNodes), port)
      case _ =>
        println("supplied wrong role")
        System.exit(1)
    }
  }

  def startup(role: Role, port: Int): Unit = {
    // Override the configuration of the port
    val config = ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
      akka.cluster.roles = [$role]
      """).withFallback(ConfigFactory.load())

    role match {
      case DataNodeRole => {
        val system =
          ActorSystem[Nothing](DataNodeRootBehavior(), "ClusterSystem", config)
      }
      case GatewayNodeRole(numDataNodes) => {
        val system =
          ActorSystem(GatewayNode.actorBehavior(numDataNodes), "ClusterSystem", config)
        GatewayHttpServer.run("0.0.0.0", 8080, system)
      }
    }
  }

  object DataNodeRootBehavior {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>
      println("I am receiver")
      context.spawn(DataNode(), "receiverNode")
      Behaviors.empty
    }
  }
}
