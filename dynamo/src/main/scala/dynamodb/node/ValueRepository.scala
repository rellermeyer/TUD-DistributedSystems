package dynamodb.node

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.VectorClock

// Definition of a build job and its possible status values
object ValueRepository {

  final case class Value(key: String, value: String, version: VectorClock = new VectorClock())

  // Trait defining successful and failure responses
  sealed trait Response

  case object OK extends Response

  final case class KO(reason: String) extends Response

  // Trait and its implementations representing all possible messages that can be sent to this Behavior
  sealed trait Command

  final case class AddValue(value: Value, replyTo: ActorRef[Response]) extends Command

  final case class GetValueByKey(key: String, replyTo: ActorRef[Option[Value]]) extends Command

  final case class RemoveValue(key: String, replyTo: ActorRef[Response]) extends Command

  final case class ClearValues(replyTo: ActorRef[Response]) extends Command


  // This behavior handles all possible incoming messages and keeps the state in the function parameter
  def apply(nodeName: String, values: Map[String, Value] = Map.empty): Behavior[Command] = Behaviors.receiveMessage {
    case AddValue(value, replyTo) =>
      values.get(value.key) match {
        case Some(previousValue) if value.version > previousValue.version =>
          replyTo ! OK
          ValueRepository(nodeName, values.+(value.key -> value))
        case Some(_) =>
          replyTo ! KO("Version too old")
          Behaviors.same
        case None =>
          replyTo ! OK
          ValueRepository(nodeName, values.+(value.key -> Value(value.key, value.value, value.version)))
      }
    case GetValueByKey(id, replyTo) =>
      replyTo ! values.get(id)
      Behaviors.same
    case RemoveValue(id, replyTo) =>
      if (values.contains(id)) {
        replyTo ! OK
        ValueRepository(nodeName, values.removed(id))
      } else {
        replyTo ! KO("Not Found")
        Behaviors.same
      }
    case ClearValues(replyTo) =>
      replyTo ! OK
      ValueRepository(nodeName, Map.empty)
  }
}
