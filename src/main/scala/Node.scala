import akka.actor.{Actor, ActorLogging}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberEvent, MemberRemoved, MemberUp, UnreachableMember}

class SimpleClusterListener extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent], classOf[UnreachableMember])
  }

  override def postStop():Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberUp(member) => log.info("Member up: {}", member.address)
    case UnreachableMember(member) => log.info("Member unreachable: {}", member.address)
    case MemberRemoved(member, previousState) =>
      log.info("Member removed: {}, with state {}", member.address, previousState)
    case _: MemberEvent =>

  }
}