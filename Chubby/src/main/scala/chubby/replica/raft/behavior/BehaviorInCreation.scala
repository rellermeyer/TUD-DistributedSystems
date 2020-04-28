package chubby.replica.raft.behavior

import java.net.InetAddress
import java.util.concurrent.TimeoutException

import akka.actor.ActorRef
import akka.actor.typed.scaladsl.adapter.actorRefAdapter
import akka.actor.{ActorSystem, Cancellable}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import chubby.replica.raft.ProtocolRaft.RaftServerAnnounce
import chubby.replica.raft.{LockState, LogState, ProtocolRaft, RaftServerState}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Random, Success}

object BehaviorInCreation {
  def apply(
      system: ActorSystem,
      allActorRefRaftServerOther: Map[String, akka.actor.typed.ActorRef[ProtocolRaft.Command]]
  ): Behavior[ProtocolRaft.Command] = {
    Behaviors.receive((context, message) => {
      val ipAddressOwn = InetAddress.getLocalHost.getHostAddress

      message match {
        case ProtocolRaft.RaftServerAnnounce(ipAddress) if ipAddress != ipAddressOwn =>
          val actorRef =
            try {
              actorRefAdapter(
                Await.result(
                  system.actorSelection(s"akka.tcp://server@${ipAddress}:2552/user/RaftActor").resolveOne(3.seconds),
                  3.seconds
                )
              )
            } catch {
              case _: TimeoutException => actorRefAdapter(system.deadLetters)
            }

          BehaviorInCreation(
            system,
            allActorRefRaftServerOther + (ipAddress -> actorRef)
          )
        case ProtocolRaft.StartServer =>
          println("Starting server")

          allActorRefRaftServerOther.values.foreach(actorRef => actorRef.tell(RaftServerAnnounce(ipAddressOwn)))

          val initialLogIndexNextMap = allActorRefRaftServerOther.map(entry => entry._1 -> 0)
          val initialLogIndexMatchMap = allActorRefRaftServerOther.map(entry => entry._1 -> -1)

          BehaviorFollower(
            RaftServerState(
              ipAddressOwn,
              system,
              context,
              None,
              0,
              allActorRefRaftServerOther,
              0,
              LockState(List.empty, List.empty),
              scheduleInitialTimeout(context),
              LogState(List.empty, -1, -1, initialLogIndexNextMap, initialLogIndexMatchMap)
            )
          )
        case _ => Behaviors.same
      }
    })
  }

  private def scheduleInitialTimeout(context: ActorContext[ProtocolRaft.Command]): Cancellable = {
    val randomGenerator = Random
    val delay = (randomGenerator.nextInt(10) + 15).seconds

    context.scheduleOnce(delay, context.self, ProtocolRaft.TimeOut)
  }
}
