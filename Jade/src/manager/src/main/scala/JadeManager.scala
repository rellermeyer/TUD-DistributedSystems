import WrapperId.WrapperId
import akka.actor.{ActorSystem, Props}

import scala.concurrent.ExecutionContext
import scala.util.Random

/**
  * Central manager for the Jade system.
  *
  * Deploys the managed application and the managed architecture.
  * Is the portal to manage the JADE system.
  */
object JadeManager {
    implicit val ec: ExecutionContext = ExecutionContext.global
    val system: ActorSystem = JadeLauncher.system
    var managedArchitecture: ManagedArchitecture = new ManagedArchitecture()
    private var config: ManagerConfig = _
    var isLeader = true
    var usingReplicas = false

    /**
      * Sets up connections with the other replicas.
      *
      * Should be called before starting the architecture.
      *
      * @param id         the ID of this replica
      * @param peerIdToIp mapping of IDs to IP addresses of peer replicas
      */
    def connectToReplicas(id: Int, peerIdToIp: Map[Int, String], onComplete: () => Any): Unit = {
        usingReplicas = true
        isLeader = id == 0

        val replicaRef = system.actorOf(Props(new JadeManagerReplica(id, peerIdToIp, system)), name = "JadeManagerReplica")
        replicaRef ! Init(onComplete)
    }

    /**
      * Starts the system by starting all the necessary managers and the managed architecture.
      *
      * @param ipList the list of IP addresses of the hardware nodes.
      * @param config the config this manager will use.
      */
    def start(ipList: List[String], config: ManagerConfig): Unit = {
        this.config = config

        if (isLeader) {
            println("Starting Jade as Leader")
            managedArchitecture.hardwareNodeIps = ipList
            managedArchitecture.freeHardwareNodeIps ++= ipList
            // Deploying nodes
            for (node <- config.nodes) {
                deployNode(node.id)
            }

            // Deploying wrappers on nodes
            // Separated so all wrappers are already created for the bindings.
            for (node <- config.nodes) {
                for (wrapper <- node.wrappers) {
                    val wrapperId = WrapperId.stringToId(wrapper)
                    val filteredBindings = node.bindings.filter { w => w.fromWrapper.equals(wrapper) }
                    if (filteredBindings.isEmpty) {
                        deployWrapper(managedArchitecture.nodeMirrors(node.id), wrapperId)
                    } else {
                        deployWrapper(managedArchitecture.nodeMirrors(node.id), wrapperId, Some(filteredBindings.head))
                    }
                }
            }
        }

        if (!usingReplicas) {
            // Use old heartbeats in case we don't use replication.
            initiateHeartbeatScheduling()
        }
    }

    /**
      * Deploys a node mirror by adding it to the managed architecture.
      *
      * @return the deployed node mirror
      */
    def deployNode(id: String): NodeMirror = {
        println(s"deploying node: $id")
        if (managedArchitecture.freeHardwareNodeIps.isEmpty) {
            println("No hardware nodes available")
            return null
        }
        val ip = managedArchitecture.freeHardwareNodeIps.remove(0)
        val nodeMirror = new NodeMirror(id, ip)
        managedArchitecture.nodeMirrors += (id -> nodeMirror)
        nodeMirror
    }

    /**
      * Removes a node mirror by removing it from the managed architecture.
      *
      * @param nodeMirror the node mirror to be removed
      */
    def removeNode(nodeMirror: NodeMirror): Unit = {
        println(s"removing node: $nodeMirror.id")
        val ip = nodeMirror.ip
        managedArchitecture.freeHardwareNodeIps += ip
        managedArchitecture.nodeMirrors -= nodeMirror.id
    }

    /**
      * Deploys a wrapper with the given id on the given node mirror.
      *
      * @param nodeMirror the mirror of the node to deploy the wrapper on
      * @param wrapperId  the ID of the wrapper to deploy
      */
    def deployWrapper(nodeMirror: NodeMirror, wrapperId: WrapperId, binding: Option[BindingConfig] = None): Unit = {
        println(s"deploying wrapper: $wrapperId on node ${nodeMirror.id}")
        nodeMirror.startWrapper(wrapperId, binding)
    }

    /**
      * Initiates the scheduling of a heartbeat at a regular interval.
      * Only use this if we don't use replication.
      */
    private def initiateHeartbeatScheduling(): Unit = {
        system.scheduler.schedule(ConnectionConfig.heartbeatInterval, ConnectionConfig.heartbeatInterval)({
            managedArchitecture.nodeMirrors.foreach {
                _._2.sendHeartbeat()
            }
        })

        system.scheduler.schedule(ConnectionConfig.wrapperFailureInterval, ConnectionConfig.wrapperFailureInterval)({
            requestWrapperFail()
        })
    }

    private def requestWrapperFail(): Unit = {
        val victim = Random.shuffle(managedArchitecture.nodeMirrors.toList).head
        val victimWrapper = Random.shuffle(victim._2.wrappers.toList).head
        print(s"Requesting failure of wrapper: $victimWrapper._1 from node: $victim._1")
        victim._2.failWrapper(victimWrapper._1)
    }

    case class NodeConfig(id: String, wrappers: List[String], bindings: List[BindingConfig])

    case class BindingConfig(toNode: String, fromWrapper: String, toWrapper: String)

    case class ManagerConfig(nodes: List[NodeConfig])

}
