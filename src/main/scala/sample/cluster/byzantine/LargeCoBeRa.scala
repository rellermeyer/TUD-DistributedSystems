package sample.cluster.byzantine

import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import sample.cluster.CborSerializable
import sample.cluster.byzantine.Node.Event
import sample.cluster.byzantine.SyncNode.{EPISOLON, LargeCoreBaResponse, T}

import scala.util.Random

object LargeCoBeRa {
  val CoBeRaServiceKey: ServiceKey[LargeCoBeRa.Command] = ServiceKey[LargeCoBeRa.Command]("largeCoBeRa")

  sealed trait Command extends CborSerializable

  case class PrepareCoreBA(seqNum: Int, senderID: Int, readyIn: Int, trusting: Boolean, replyTo: ActorRef[SyncNode.Ack]) extends Command

  case class AgreementSelectionPhaseOne(replyTo: ActorRef[SyncNode.LargeCoreBaResponse]) extends Command
  case class AgreementSelectionPhaseTwo(replyTo: ActorRef[SyncNode.LargeCoreBaResponse]) extends Command

  case class RegisterActiveNode(nodeID: Int) extends Command
  case class RegisterLightNode(nodeID: Int) extends Command

  def apply(numberOfNodes: Int): Behavior[Command] = Behaviors.setup { ctx =>
    ctx.log.info("LargeCoBeRa nodes has been setup")
    ctx.system.receptionist ! Receptionist.Register(CoBeRaServiceKey, ctx.self)

    var activeNodes = List.empty[Int]
    var lightNodes = List.empty[Int]
    var core = Map.empty[Int, (Int, Boolean)]

    val low: Double = numberOfNodes - 2 * T - EPISOLON * numberOfNodes
    val high: Double = low + T
    var mode: Int = -1

    Behaviors.receiveMessage {
      case PrepareCoreBA(segNum, senderID, readyIn, trusting, replyTo) =>
        core = core + (senderID -> (readyIn, trusting))
        replyTo ! SyncNode.Ack(segNum)
        Behaviors.same
      case AgreementSelectionPhaseOne(replyTo) =>
        if (lightNodes.size >= high) replyTo ! LargeCoreBaResponse(1, -1)
        else if (lightNodes.size >= low && lightNodes.size < high) {
          mode = if (mode == -1) Random.nextInt() else mode
          replyTo ! LargeCoreBaResponse(mode, -1)
        } else replyTo ! LargeCoreBaResponse(0, -1)

        Behaviors.same

      case AgreementSelectionPhaseTwo(replyTo) =>
        if (lightNodes.size >= high) replyTo ! LargeCoreBaResponse(-1, 1)
        else if (lightNodes.size >= low && lightNodes.size < high) {
          mode = if (mode == -1) Random.nextInt() else mode
          replyTo ! LargeCoreBaResponse(-1, mode)
        } else replyTo ! LargeCoreBaResponse(-1, 0)
        Behaviors.same
      case RegisterActiveNode(nodeID) =>
        activeNodes = activeNodes :+ nodeID
        Behaviors.same
      case RegisterLightNode(nodeID) =>
        lightNodes = lightNodes :+ nodeID
        Behaviors.same
    }
  }
}
