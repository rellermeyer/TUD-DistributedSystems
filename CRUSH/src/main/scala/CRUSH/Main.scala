package CRUSH

import CRUSH.OSD.OSDNode
import CRUSH.controller.{ Root, RootController, Rootstore }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.typed.Cluster
import com.typesafe.config.{ Config, ConfigFactory }

object Main {

  sealed trait Role

  case class OSDNodeRole(identifier: Int, totalStorage: Int) extends Role

  case class RootNodeRole(expectedNumberOSD: Int) extends Role

  object NodeBehavior {
    def apply(role: Role): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      val cluster = Cluster(ctx.system)
      role match {
        case RootNodeRole(expectedNumberOSD) =>
          // Initialize the RootStore using the RootController object
          Rootstore.configMap = RootController.readMap()
          Rootstore.placementRule = RootController.readRules()
          ctx.log.info(Rootstore.configMap.toString)
          ctx.log.info(Rootstore.placementRule.toString)
          ctx.spawn(Root(expectedNumberOSD), s"root")
        case OSDNodeRole(identifier, totalStorage) =>
          ctx.spawn(OSDNode(identifier, totalStorage), s"Worker-OSD-$identifier")
      }
      Behaviors.empty
    }
  }

  def spinUpNode(role: Role, config: Config): Unit = {

    ActorSystem[Nothing](NodeBehavior(role), "ClusterSystem", config)

  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    args(0) match {
      case "osd" =>
        spinUpNode(OSDNodeRole(args(1).toInt, args(2).toInt), config)
      case "root" =>
        spinUpNode(RootNodeRole(args(1).toInt), config)
      case "test" =>
        var port = 25251
        var config = ConfigFactory
          .parseString(s"""
      akka.remote.artery.canonical.port=${port}
      """).withFallback(ConfigFactory.load())
        spinUpNode(RootNodeRole(3), config)

        (0 to 2).toList.foreach((id) => {
          port += 1
          config = ConfigFactory
            .parseString(s"""
      akka.remote.artery.canonical.port=${port}
      """).withFallback(ConfigFactory.load())
          spinUpNode(OSDNodeRole(id, 4000), config)

        })

      case _ =>
        println("Cant handle this")
        System.exit(-1);
    }
  }
}
