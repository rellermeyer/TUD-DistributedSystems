package sample.cluster.byzantine


import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.ClusterEvent.{MemberEvent, MemberUp}
import akka.cluster.Member
import akka.cluster.typed.{Cluster, Subscribe}
import sample.cluster.CborSerializable


object Node {
  val NodeServiceKey: ServiceKey[Event] = ServiceKey[Node.Event]("Nodes")

  private final val C = 2

  sealed trait Event
  private final case class MemberChange(event: MemberEvent) extends Event
  private final case class NodesUpdated(newWorkers: Set[ActorRef[Node.Event]]) extends Event
  private final case class LargeCoBeRaUpdated(largeCoBeRa: Set[ActorRef[LargeCoBeRa.AgreementSelection]]) extends Event

  sealed trait Request extends Event with CborSerializable
  case class PrintMyId(id: Int) extends Request
  case class RegisterMyId(id: Int, replyTo: ActorRef[Node.Event]) extends Request
  case class SelectedId(senderId: Int, selectedId: Int, replyTo: ActorRef[Node.Event]) extends Request
  
  private final case class RegisterNodeId(id: Int, fromId: ActorRef[_])

  sealed trait Response extends Event with CborSerializable

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


      // define the state of the actor
      val r = scala.util.Random
      val p: Double = (C * math.log(numberOfNodes))/numberOfNodes
      var readyOut: Int = 0
      var readyIn: Int = 0
      val active: Boolean = r.nextDouble() <= p
      var light: Boolean = false

      ctx.log.info(s"I am node: $nodeId and im ${if (active) "active" else "not active"}")

      //      if (r.nextDouble() <= p)
//        nodesReferences.foreach { node => node ! Node.PrintMyId(nodeId) }



      //      running(ctx, IndexedSeq.empty)
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
          if (id != nodeId) activeNodes = activeNodes + (id -> replyTo)
          light = activeNodes.size < 2
          val list: List[(Int, ActorRef[Event])] = activeNodes.toList
          println(list)
          val (selectedId: Int, _) = list(r.nextInt(list.size))
          activeNodes.values.foreach(ref => ref ! Node.SelectedId(nodeId, selectedId, ctx.self))
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
