import ComponentState.ComponentState
import WrapperId.WrapperId

/**
  * Message for resetting the node, i.e. stopping all wrappers on it.
  */
final case class ResetNode()

/**
  * Message for starting the wrapper with the given ID on this node.
  *
  * @param id the ID of the wrapper that should be deployed on this node
  */
final case class StartWrapper(id: WrapperId, target: Option[Address] = None)

/**
  * Message for stopping the wrapper with the given ID on this node.
  *
  * @param id the ID of the wrapper that should be stopped on this node
  */
final case class StopWrapper(id: WrapperId)

/**
  * Message for restarting the wrapper with the given ID on this node.
  *
  * @param id the ID of the wrapper that should be restarted
  */
final case class RestartWrapper(id: WrapperId, targets: Option[Address] = None)

/**
  * Message for failing the wrapper with the given ID on this node.
  *
  * @param id the ID of the wrapper that should be failed
  */
final case class FailWrapper(id: WrapperId)

/**
  * Periodic heartbeat message representing a request for a state update.
  */
final case class Heartbeat()

/**
  * Message for the heartbeat response containing the node's and its wrappers' state.
  *
  * @param nodeState     the state of the node sending this message
  * @param wrapperStates the states of the wrappers belonging to the node sending this message
  */
final case class HeartbeatResponse(nodeState: ComponentState, wrapperStates: Map[WrapperId, ComponentState])

/**
  * Message for wrapper configuration.
  *
  * @param id            the ID of the wrapper to configure
  * @param configuration the configuration to set
  */
final case class ConfigureWrapper(id: WrapperId, configuration: WrapperConfiguration)

/**
  * Message for binding a wrapper to another wrapper.
  *
  * @param id   the ID of the wrapper to bind
  * @param ip   the IP to which the wrapper should be bound
  * @param port the port number to which the wrapper should be bound
  */
final case class Bind(id: WrapperId, ip: String, port: String)

/**
  * Message for unbinding a wrapper from another wrapper.
  *
  * @param id   the ID of the wrapper to bind
  * @param ip   the IP of the wrapper from which this wrapper should be unbound
  * @param port the port number from which this wrapper should be unbound
  */
final case class Unbind(id: WrapperId, ip: String, port: String)
