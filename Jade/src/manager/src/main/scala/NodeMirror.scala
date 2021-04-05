import ComponentState.ComponentState
import JadeManager.BindingConfig
import WrapperId.WrapperId
import akka.actor.{ActorIdentity, ActorRef, Identify}
import akka.pattern.ask
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}

/**
  * A mirror of a node, monitoring and controlling the node it represents.
  */
class NodeMirror(val id: String, val ip: String) extends Serializable {
    @transient implicit var ec: ExecutionContext = ExecutionContext.global
    @transient implicit var timeout: Timeout = ConnectionConfig.timeout
    @transient var nodeRef: ActorRef = connect()
    var state: ComponentState = ComponentState.Running
    var wrappers: mutable.Map[WrapperId, WrapperMirror] = mutable.Map.empty

    def init(): Unit = {
        println("Init node mirror after serialization")
        ec = ExecutionContext.global
        timeout = ConnectionConfig.timeout
        nodeRef = connect()
    }

    def connect(): ActorRef = {
        val selection = JadeManager.system.actorSelection(s"akka.tcp://Node@$ip:${ConnectionConfig.port}/user/Node")
        val futureRef = selection.resolveOne(timeout.duration)
        val resolvedSelection = Await.result(futureRef, timeout.duration)

        val identityFuture = resolvedSelection ? Identify()
        val identity = Await.result(identityFuture, timeout.duration).asInstanceOf[ActorIdentity]
        identity.getActorRef.orElseThrow(() => new RuntimeException("Unable to resolve identity of Node"))
    }

    /**
      * Stops all wrappers on the node.
      */
    def reset(): Unit = nodeRef ! ResetNode()

    /**
      * Instructs the corresponding node to start a wrapper.
      *
      * Also creates a mirror of the wrapper, representing this wrapper.
      *
      * @param id the ID of the wrapper to start
      * @return the created WrapperMirror
      */
    def startWrapper(id: WrapperId, binding: Option[BindingConfig] = None): WrapperMirror = {
        var target: Option[Address] = None
        val wrapperMirror = new WrapperMirror(id)

        if (binding.isDefined) {
            wrapperMirror.binding = binding.get
            target = Some(convertBindingsToAddress(binding.get))
        }

        nodeRef ! StartWrapper(id, target)
        wrappers += (id -> wrapperMirror)
        wrapperMirror
    }

    /**
      * Instructs the corresponding node to stop a wrapper.
      *
      * Also removes a mirror of the wrapper, representing this wrapper.
      *
      * @param id the ID of the wrapper to stop
      */
    def stopWrapper(id: WrapperId): Unit = {
        nodeRef ! StopWrapper(id)
        wrappers.remove(id)
    }

    /**
      * Instruct the corresponding node to restart a wrapper.
      *
      * @param id the id of the wrapper
      */
    def restartWrapper(id: WrapperId): Unit = {
        val binding = wrappers(id).binding
        if (binding != null) {
            nodeRef ! RestartWrapper(id, Some(convertBindingsToAddress(binding)))
        } else {
            nodeRef ! RestartWrapper(id)
        }
    }

    /**
      * Instructs the corresponding node to configure the given wrapper with the given configuration.
      *
      * Also stores the configuration in the corresponding wrapper mirror.
      *
      * @param id            the ID of the wrapper to configure
      * @param configuration the configuration to use
      */
    def configureWrapper(id: WrapperId, configuration: WrapperConfiguration): Unit =
        nodeRef ! ConfigureWrapper(id, configuration)

    /**
      * Will request a random wrapper to fail.
      */
    def failWrapper(id: WrapperId): Unit = {
        nodeRef ! FailWrapper(id)
    }

    /**
      * Sends a heartbeat to determine the state of the node this mirror manages.
      *
      * Should be invoked periodically from the containing entity.
      */
    def sendHeartbeat(): Unit = {
        if (nodeRef == null) {
            return
        }
        println(s"Sending heartbeat to node: $id")
        val futureHeartbeatResponse = nodeRef ? Heartbeat()
        futureHeartbeatResponse.onComplete {
            case Success(heartbeatResponse) =>
                println(s"Heartbeat successful: $id, response $heartbeatResponse")
                handleStateUpdate(heartbeatResponse.asInstanceOf[HeartbeatResponse])
            case Failure(_) =>
                println(s"Heartbeat failed: $id")
                handleStateUpdate(HeartbeatResponse(ComponentState.Failed, Map.empty))
        }
    }

    /**
      * Converts a binding to an address.
      *
      * @param binding a binding
      * @return an address corresponding to the given binding
      */
    private def convertBindingsToAddress(binding: BindingConfig): Address = {
        val targetIp = JadeManager.managedArchitecture.nodeMirrors(binding.toNode).ip
        val targetPort = WrapperId.idToPort(WrapperId.stringToId(binding.toWrapper))
        new Address(targetIp, targetPort)
    }

    /**
      * Saves the given state returned by the heartbeats and reports failures to the repair manager.
      *
      * @param heartbeatResponse response to the earlier Heartbeat message from the node
      */
    private def handleStateUpdate(heartbeatResponse: HeartbeatResponse): Unit = {
        state = heartbeatResponse.nodeState

        if (state == ComponentState.Failed) JadeManager.managedArchitecture.repairManager.nodeFailed(this)

        heartbeatResponse.wrapperStates.foreach {
            case (wrapperId, componentState) =>
                wrappers.get(wrapperId).orNull.state = componentState
                if (componentState == ComponentState.Failed) {
                    JadeManager.managedArchitecture.repairManager.wrapperFailed(this, wrapperId)
                }
        }
    }
}
