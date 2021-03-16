package sample.cluster.byzantine

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.typed.Cluster
import com.typesafe.config.ConfigFactory

object App {
  object RootBehavior {
    val r = scala.util.Random

    def apply(id: Int): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)

      if (cluster.selfMember.hasRole("node")) {
//        ctx.spawn(Node(r.nextInt(100), 2), s"Node$id")
        ctx.spawn(SyncNode(id, 10), s"Node$id")
      }
      if (cluster.selfMember.hasRole("decider")) {
        ctx.spawn(LargeCoBeRa(), "LargeCoBeRa")
      }
      Behaviors.empty
    }
  }

  def main(args: Array[String]) = {
    if (args.isEmpty) {
      startUp("decider", 25251, 0)
      for (i <- 1 to 10) {
        startUp("node", 0, i)
      }
    } else {
      require(args.length == 3, "Usage: role port")
      startUp(args(0), args(1).toInt, args(2).toInt)
    }
  }

  def startUp(role: String, port: Int, id: Int): Unit = {
    // Override the configuration of the port and role
    val config = ConfigFactory
      .parseString(s"""
        akka.remote.artery.canonical.port=$port
        akka.cluster.roles = [$role]
        """)
      .withFallback(ConfigFactory.load("byzantine"))

    ActorSystem[Nothing](RootBehavior(id), "ClusterSystem", config)
  }
}
