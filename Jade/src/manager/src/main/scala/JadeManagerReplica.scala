import akka.actor.{Actor, ActorIdentity, ActorRef, ActorSystem, Identify}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Success

/**
  * Class ensuring that only one JadeManager replica is active, at a time.
  *
  * @param id         the ID of this JadeManager (should be unique across replicas)
  * @param peerIdToIp a mapping from IDs to IP addresses of peer JMs
  * @param system     the ActorSystem instance
  */
class JadeManagerReplica(val id: Int, val peerIdToIp: Map[Int, String], val system: ActorSystem) extends Actor {
    implicit val ec: ExecutionContext = ExecutionContext.global
    implicit val timeout: Timeout = ConnectionConfig.timeout

    var peerIdToIsAlive: Map[Int, Boolean] = peerIdToIp.map {
        case (peerId, _) => (peerId, true)
    }
    var peerIdToActorRef: Map[Int, ActorRef] = Map.empty

    /**
      * Checks which JadeManager replicas are still alive.
      *
      * @param onComplete callback to be called when the heartbeat has been executed and all replies are in
      */
    def heartbeat(onComplete: () => Any): Unit = {
        println("Sending replica heartbeats")
        var futures: ListBuffer[Future[Any]] = ListBuffer.empty
        peerIdToIsAlive.filter(_._2).foreach {
            case (peerId, _) =>
                val futureHeartbeat = peerIdToActorRef(peerId) ? JadeManagerReplicaHeartbeat()
                futureHeartbeat.onComplete {
                    case Success(response) =>
                        peerIdToIsAlive += (peerId -> true)
                        response match {
                            case IsLeaderResponse(managedArchitecture: ManagedArchitecture) =>
                                JadeManager.managedArchitecture = managedArchitecture

                                if (peerId > id) {
                                    println("Invalid leader state detected")
                                }
                            case _ =>
                        }
                    case _ =>
                        println(s"Replica $peerId did not respond")
                        peerIdToIsAlive += (peerId -> false)
                }
                futures += futureHeartbeat
        }
        Future {
            FutureUtil.executeFutures(futures, () => {
                checkIfLeader()
                onComplete()
            })
        }
    }

    /**
      * Receives peer heartbeat messages.
      */
    def receive: PartialFunction[Any, Unit] = LoggingReceive {
        case JadeManagerReplicaHeartbeat() =>
            println("Received replica heartbeat")
            if (JadeManager.isLeader) {
                sender ! IsLeaderResponse(JadeManager.managedArchitecture)
            } else {
                sender ! IsNotLeaderResponse()
            }
        case Init(onCompleted) => Future {
            init(onCompleted)
        }
    }

    private def init(onCompleted: () => Any): Unit = {
        try {
            peerIdToActorRef = peerIdToIp.map {
                case (peerId, peerIp) => (peerId, connectToPeer(peerIp))
            }
            initiateHeartbeatScheduling()
            onCompleted()
        }
        catch {
            case _: Throwable => {
                println("retrying")
                Thread.sleep(ConnectionConfig.timeout.duration.toMillis)
                init(onCompleted)
            }
        }
    }

    /**
      * Connects to the given peer replica.
      *
      * @param peerIp the IP address of the peer
      * @return a ActorRef to the peer
      */
    private def connectToPeer(peerIp: String): ActorRef = {
        val selection = system.actorSelection(s"akka.tcp://Manager@$peerIp:${ConnectionConfig.managerPort}/user/JadeManagerReplica")
        var futureRef: Future[ActorRef] = null
        while (true) {
            futureRef = selection.resolveOne(timeout.duration)
            val resolvedSelection = Await.result(futureRef, timeout.duration)

            if (resolvedSelection.isInstanceOf[ActorRef]) {
                val identityFuture = resolvedSelection ? Identify()
                val identity = Await.result(identityFuture, timeout.duration).asInstanceOf[ActorIdentity]
                return identity.getActorRef.orElseThrow(() => new RuntimeException("Unable to resolve identity of JadeManager"))
            }

            println("Could not connect to peer replicas, trying again in 3 seconds")
            Thread.sleep(ConnectionConfig.timeout.duration.toMillis)
        }
        null
    }

    /**
      * Checks if the current replica is the leader.
      */
    private def checkIfLeader(): Unit = {
        val wasLeader = JadeManager.isLeader
        val allAliveIds = List(id) ++ peerIdToIsAlive.filter {
            _._2
        }.keys.toList
        val sortedAliveIds = allAliveIds.sortWith((a, b) => a < b)
        JadeManager.isLeader = sortedAliveIds.nonEmpty && sortedAliveIds.head == id
        if (!wasLeader && JadeManager.isLeader) {
            println("This replica is now leader")
            for ((_, nodeMirror: NodeMirror) <- JadeManager.managedArchitecture.nodeMirrors) {
                nodeMirror.init()
            }
        }
    }

    /**
      * Initiates the scheduling of a heartbeat at a regular interval.
      */
    private def initiateHeartbeatScheduling(): Unit = {
        system.scheduler.schedule(ConnectionConfig.heartbeatInterval, ConnectionConfig.heartbeatInterval)({
            heartbeat(() => {
                if (JadeManager.isLeader) {
                    JadeManager.managedArchitecture.nodeMirrors.foreach {
                        _._2.sendHeartbeat()
                    }
                }
            })
        })
    }
}

case class JadeManagerReplicaHeartbeat()

case class IsLeaderResponse(managedArchitecture: ManagedArchitecture) extends Serializable

case class IsNotLeaderResponse()

case class Init(onCompleted: () => Any)
