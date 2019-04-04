import ComponentState.ComponentState

import scala.concurrent.Future

/**
  * A wrapper component, monitoring and controlling an application component.
  *
  * @param config the wrapper configuration
  */
abstract class Wrapper(final protected val config: WrapperConfiguration) {
    /**
      * Stops this wrapper by shutting it down.
      */
    def stop(): Unit

    /**
      * Receives the heartbeat message from the node and then polls the managed application component.
      *
      * @return a Future containing the state of the component
      */
    def heartbeat(): Future[ComponentState]

    /**
      * Configures the wrapped component.
      *
      * @param wrapperConfiguration the configuration of the wrapped component to apply
      */
    def configure(wrapperConfiguration: WrapperConfiguration): Unit = ???

    /**
      * Binds the wrapper to the given IP and port.
      *
      * @param ip   the IP to which the wrapper should be bound
      * @param port the port number to which the wrapper should be bound
      */
    def bind(ip: String, port: String): Unit = ???

    /**
      * Unbinds the wrapper from the given IP and port.
      *
      * @param ip   the IP to which the wrapper should be unbound
      * @param port the port number to which the wrapper should be unbound
      */
    def unbind(ip: String, port: String): Unit = ???
}
