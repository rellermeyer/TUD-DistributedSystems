package sample.cluster.byzantine

import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import sample.cluster.CborSerializable
import sample.cluster.byzantine.Node.Event

object LargeCoBeRa {
  val CoBeRaServiceKey: ServiceKey[LargeCoBeRa.AgreementSelection] = ServiceKey[LargeCoBeRa.AgreementSelection]("largeCoBeRa")

  sealed trait Command

  final case class AgreementSelection(nodes: List[String], replyTo: ActorRef[AgreementCompleted]) extends Command with CborSerializable
  final case class AgreementCompleted(nodes: List[String], leader: String) extends CborSerializable

  def apply(): Behavior[Command] = Behaviors.setup { ctx =>
      ctx.log.info("LargeCoBeRa nodes has been setup")

      Behaviors.receiveMessage {
          case AgreementSelection(nodes, replyTo) =>
            replyTo ! AgreementCompleted(nodes, nodes.head)
            Behaviors.same
      }
  }
}
