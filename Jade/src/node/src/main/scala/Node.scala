import ComponentState.ComponentState
import WrapperId.WrapperId
import akka.actor.{Actor, ActorRef}
import akka.event.LoggingReceive

import scala.collection.mutable
import scala.concurrent.ExecutionContext

/**
  * Represents a node component, wrapping a physical machine.
  */
class Node extends Actor {
    implicit val ec: ExecutionContext = ExecutionContext.global
    var state: ComponentState = ComponentState.Running
    var wrappers: mutable.Map[WrapperId, Wrapper] = mutable.Map.empty

    /**
      * Handles incoming messages.
      */
    def receive: PartialFunction[Any, Unit] = LoggingReceive {
        case ResetNode() => resetNode()
        case StartWrapper(id, targets) => startWrapper(id, targets)
        case StopWrapper(id) => stopWrapper(id)
        case ConfigureWrapper(id, configuration) => configureWrapper(id, configuration)
        case RestartWrapper(id, targets) =>
            stopWrapper(id)
            startWrapper(id, targets)
        case Heartbeat() => heartbeat(sender())
        case FailWrapper(id) => failWrapper(id)
        //        case Bind(id, ip, port) => bind(id, ip, port)
        //        case Unbind(id, ip, port) => unbind(id, ip, port)Ω
    }

    /**
      * Resets the node, i.e. removes all wrappers.
      */
    private def resetNode(): Unit = {
        this.wrappers.foreach(v => {
            v._2.stop()
            this.wrappers.remove(v._1)
        })
    }

    /**
      * Starts the wrapper with the given ID on this node.
      *
      * @param id the ID of the wrapper that should be started on this node
      * @return the started wrapper
      */
    private def startWrapper(id: WrapperId, target: Option[Address]): Wrapper = {
        println(s"starting wrapper: $id")
        val wrapper = this.wrappers.get(id)
        wrapper match {
            case Some(w) =>
                this.error("A wrapper with type " + id + " already exists on this node.")
                w
            case None =>
                var w: Wrapper = null
                if (target.isDefined) {
                    w = WrapperFactory.build(id, target)
                } else {
                    w = WrapperFactory.build(id)
                }
                this.wrappers += ((id, w))
                w
        }
    }

    /**
      * Stops the wrapper with the given ID on this node.
      *
      * @param id the ID of the wrapper that should be stopped on this node
      */
    private def stopWrapper(id: WrapperId): Unit = {
        println(s"stopping wrapper: $id")
        val wrapper = this.wrappers.get(id)
        wrapper match {
            case Some(w) => {
                w.stop()
                wrappers.remove(id)
            }
            case None => error("Trying to stop non-existing wrapper: " + id)
        }
    }

    /**
      * Fails a wrapper with given ID on this node.
      *
      * @param id the ID of the wrapper that should be failed on this node
      */
    def failWrapper(id: WrapperId): Unit = {
        println(s"Failure of wrapper: $id")
        val wrapper = this.wrappers.get(id)
        wrapper match {
            case Some(w) => {
                w.stop()
            }
            case None => error("Trying to fail non-existing wrapper: " + id)
        }
    }


    /**
      * Handles an error.
      *
      * @param message The error message
      */
    private def error(message: String): Unit = {
        println(message)
        this.state = ComponentState.Failed
    }

    /**
      * Gets the heartbeat containing its own state as well as the state of its wrappers.
      *
      * @param actorRef a reference to the actor to respond to
      * @return the current state of this node and a map with the state of its wrappers
      */
    private def heartbeat(actorRef: ActorRef): Unit = {
        val tuples = wrappers.toSeq.unzip
        val ids = tuples._1
        val futures = tuples._2.map(_.heartbeat())
        FutureUtil.executeFutures[ComponentState](futures, (states: Seq[ComponentState]) => {
            println("Responding to heartbeat")
            actorRef ! HeartbeatResponse(state, (ids zip states).toMap)
        })
    }

    /**
      * Configures the wrapper with the given configuration.
      *
      * @param id            the ID of the wrapper to configure
      * @param configuration the configuration to set
      */
    private def configureWrapper(id: WrapperId, configuration: WrapperConfiguration): Unit = {
        val wrapper = this.wrappers.get(id)
        wrapper match {
            case Some(w) => w.configure(configuration)
            case None => error("Trying to configure non-existing wrapper: " + id)
        }
    }

    //    /**
    //      * Binds the given wrapper to the given IP and port.
    //      *
    //      * @param id   the ID of the wrapper to bind
    //      * @param ip   the IP to which the wrapper should be bound
    //      * @param port the port number to which the wrapper should be bound
    //      */
    //    private def bind(id: WrapperId, ip: String, port: String): Unit = {
    //        val wrapper = this.wrappers.get(id)
    //        wrapper match {
    //            case Some(w) => w.bind(ip, port)
    //            case None => error("Trying to bind non-existing wrapper: " + id)
    //        }
    //    }
    //
    //    /**
    //      * Unbinds the given wrapper from the given IP and port.
    //      *
    //      * @param id   the ID of the wrapper to bind
    //      * @param ip   the IP of the wrapper from which this wrapper should be unbound
    //      * @param port the port number from which this wrapper should be unbound
    //      */
    //    private def unbind(id: WrapperId, ip: String, port: String): Unit = {
    //        val wrapper = this.wrappers.get(id)
    //        wrapper match {
    //            case Some(w) => w.unbind(ip, port)
    //            case None => error("Trying to unbind non-existing wrapper: " + id)
    //        }
    //    }
}
