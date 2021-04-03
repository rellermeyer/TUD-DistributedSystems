package sample.cluster.byzantine

import akka.actor.Address
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.cluster.typed.{Cluster, Leave}
import com.typesafe.config.ConfigFactory
import sample.cluster.CborSerializable

import java.io.{BufferedWriter, FileWriter, PrintWriter}

object App {
  sealed trait Event extends CborSerializable
  case class cycleOutcome(nodeId: Int, valueX: Int) extends Event
  case class respawnNode(nodeId: Int, p: Double) extends Event

  object RootBehavior {
    val r = scala.util.Random

    def apply(id: Int): Behavior[App.Event] = Behaviors.setup[App.Event] { ctx =>
      val cluster = Cluster(ctx.system)
      println(cluster.subscriptions)

      if (cluster.selfMember.hasRole("node")) {
//        ctx.spawn(Node(r.nextInt(100), 2), s"Node$id")
        ctx.spawn(SyncNode(id, 10, ctx.self), s"Node$id")
      }
      if (cluster.selfMember.hasRole("decider")) {
        ctx.spawn(LargeCoBeRa(10), "LargeCoBeRa")
      }
      if (cluster.selfMember.hasRole("badnode")) {
        val node = SyncNode(id, 1, ctx.self)
        val spawnedNode = ctx.spawn(node, s"BadNode$id")
        spawnedNode ! SyncNode.MakeBad()
      }
      Behaviors.receiveSignal[Nothing] {
        case (ctx, PostStop) =>
          ctx.log.info("Some node has stopped")
          Behaviors.same
      }

      Behaviors.receiveMessage {
        case cycleOutcome(nodeId, valueX) =>
          println(s"Node $nodeId is removed from the network with $valueX")
          cluster.manager ! Leave(cluster.selfMember.address)
          Behaviors.same
        case respawnNode(nodeId, p) =>
          println(s"Node $nodeId is going to respawn")
          cluster.manager ! Leave(cluster.selfMember.address)
          ctx.spawn(SyncNode(nodeId, 10, ctx.self), s"Node$nodeId")
          Behaviors.same
      }
    }
  }

  def main(args: Array[String]) = {
    val pw = new PrintWriter("evaluation_output.csv")
    val bw = new BufferedWriter(new FileWriter("evaluation_output.csv", true))
    bw.write("nodeId,roundId,good,seqNum,active,light,value\n")
    bw.close()
    if (args.isEmpty) {
      startUp("decider", 25251, 0)
      for (i <- 1 to 10) {
        startUp("node", 0, i)
      }

      startUp("badnode", 0, 11)

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

    ActorSystem[App.Event](RootBehavior(id), "ClusterSystem", config)
  }
}
