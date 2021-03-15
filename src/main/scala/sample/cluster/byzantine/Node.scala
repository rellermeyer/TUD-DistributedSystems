package sample.cluster.byzantine


import scala.util.Failure
import scala.util.Success
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp}
import akka.cluster.Member
import akka.cluster.typed.{Cluster, Subscribe}
import akka.util.Timeout
import sample.cluster.CborSerializable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt


object Node {
  val NodeServiceKey: ServiceKey[Event] = ServiceKey[Node.Event]("Nodes")

  private final val C = 2
  private final val EPISOLON = 0.01
  private final val T = 1

  sealed trait Event
  private final case class MemberChange(event: MemberEvent) extends Event
  private final case class NodesUpdated(newWorkers: Set[ActorRef[Node.Event]]) extends Event
  private final case class LargeCoBeRaUpdated(largeCoBeRa: Set[ActorRef[LargeCoBeRa.AgreementSelection]]) extends Event

  sealed trait Request extends Event with CborSerializable
  case class PrintMyId(id: Int) extends Request
  case class RegisterMyId(id: Int, replyTo: ActorRef[Node.Event]) extends Request
  case class SelectedId(senderId: Int, selectedId: Int, replyTo: ActorRef[Node.Event]) extends Request
  case class Query(nodeId: Int, senderId: Int, replyTo: ActorRef[Node.Event]) extends Request
  
  private final case class RegisterNodeId(id: Int, fromId: ActorRef[_])

  sealed trait Response extends Event with CborSerializable
  case class QueryReply(senderId: Int, validatedId: Int) extends Response

  def apply(nodeId: Int, numberOfNodes: Int): Behavior[Event] =
    Behaviors.setup { ctx =>
      val subscriptionAdapter = ctx.messageAdapter[Receptionist.Listing] {
        case Node.NodeServiceKey.Listing(workers) =>
          NodesUpdated(workers)
        case LargeCoBeRa.CoBeRaServiceKey.Listing(largeCoBeRa) =>
          LargeCoBeRaUpdated(largeCoBeRa)
      }

      ctx.system.receptionist ! Receptionist.Subscribe(Node.NodeServiceKey, subscriptionAdapter)

      // each worker registers themselves with the receptionist
      ctx.log.info("Registering new node")
      ctx.system.receptionist ! Receptionist.Register(NodeServiceKey, ctx.self)

      val memberEventAdapter: ActorRef[MemberEvent] = ctx.messageAdapter(MemberChange)
      Cluster(ctx.system).subscriptions ! Subscribe(memberEventAdapter, classOf[MemberEvent])

      val memberRole = Map.empty[String, Member]
      val largeCoBERa = List.empty[ActorRef[LargeCoBeRa.AgreementSelection]]
      var nodesReferences = IndexedSeq.empty[ActorRef[Node.Event]]
      var activeNodes = Map.empty[Int, ActorRef[Node.Event]]
      var selectedActiveNodes = Map.empty[Int, ActorRef[Node.Event]]
      var selectedIdPerNode = Map.empty[Int, Int]

      // define the state of the actor
      val r = scala.util.Random
      val p: Double = (C * math.log(numberOfNodes))/numberOfNodes
      val low: Double = numberOfNodes - 2 * T - EPISOLON * numberOfNodes
      val high: Double = low + T
      val minA: Double = (1 - EPISOLON) * p * (numberOfNodes - T)
      val maxA: Double = (1 + EPISOLON) * p * (numberOfNodes - T)
      val beta: Double = (1 - EPISOLON) * (low - T) / (maxA + EPISOLON * p * numberOfNodes)
      val delta: Double = (1 - EPISOLON) * (low - T) / numberOfNodes
      var readyOut: Int = 0
      var readyIn: Int = 0
      val active: Boolean = r.nextDouble() <= p
      var light: Boolean = false

      ctx.log.info(s"I am node: $nodeId and im ${if (active) "active" else "not active"}")

      Behaviors.receiveMessage {
        case NodesUpdated(newWorkers) =>
          ctx.log.info("List of services registered with the receptionist changed: {}", newWorkers)
          nodesReferences = nodesReferences ++ newWorkers.toIndexedSeq
          if (active) nodesReferences.foreach { node => node ! Node.RegisterMyId(nodeId, ctx.self) }
          Behaviors.same
        case MemberChange(event) => event match {
          case MemberUp(member) =>
            if (member.hasRole("decider")) memberRole + ("decider" -> member) else memberRole + ("node" -> member)
            Behaviors.same
          case _ => Behaviors.same
        }
        case RegisterMyId(id, replyTo) =>
          if (id != nodeId) {
            activeNodes = activeNodes + (id -> replyTo)
            light = activeNodes.size < maxA + EPISOLON * p * numberOfNodes
//            light = activeNodes.size <= 2
            if (light) {
              val list: List[(Int, ActorRef[Event])] = activeNodes.toList
              val (selectedId: Int, _) = list(r.nextInt(list.size))
              activeNodes.values.foreach(ref => ref ! Node.SelectedId(nodeId, selectedId, ctx.self))
            }
          }
          Behaviors.same
        case SelectedId(senderId, selectedId, replyTo) =>
          if (active && senderId != nodeId) {
            selectedIdPerNode = selectedIdPerNode + (senderId -> selectedId)
//            println(s"I am node ${nodeId} got selectedID ${selectedIdPerNode}")
            if (selectedIdPerNode.size >= low - T) {
//              println(s"low: $low, maxA: $maxA, p: $p, beta: $beta")
              val _selectedIds = selectedIdPerNode
                .groupBy(entry => entry._2)
                .map(entry => (entry._1, entry._2.size))
                .filter(entry => entry._2 > beta)
                .keySet

              selectedActiveNodes = activeNodes.filter(entry => _selectedIds.contains(entry._1))
              selectedActiveNodes.foreach { selectedActiveNode =>
                r
                  .shuffle(nodesReferences)
                  .take(math.round(C * math.log(numberOfNodes)).toInt)
                  .foreach(ref => ref ! Node.Query(selectedActiveNode._1, nodeId, ctx.self))
              }
//              println(s"I am node ${nodeId} I selected ${_selectedIds}")
            }
          }
          Behaviors.same
        case Query(selectedId, senderId, replyTo) =>
          if (light && selectedActiveNodes.contains(selectedId) && selectedActiveNodes.contains(senderId) && senderId != nodeId) {
            replyTo ! QueryReply(nodeId, selectedId)
          }
          Behaviors.same
        case QueryReply(senderId, selectedId) =>
          println(s"I am node $nodeId I got a reply from $senderId, with $selectedId")
          Behaviors.same
        case PrintMyId(id) => {
          if (id != nodeId) ctx.log.info(s"I am node $nodeId and I received the node id: $id")
          Behaviors.same
        }
        case _ =>
          Behaviors.same
      }
    }
}
