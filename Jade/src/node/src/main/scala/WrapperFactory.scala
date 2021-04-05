import WrapperId.WrapperId

/**
  * Factory object for building wrapper instances
  */
object WrapperFactory {
    /**
      * Instantiates a wrapped of type wrapperId configured with the given ip.
      *
      * @param wrapperId the type of wrapper to instantiate
      * @param targetIp  the ip of the host the wrapper will communicate with
      * @param targetPort the port the wrapper will communicate to
      * @return a new wrapper
      */
    def build(wrapperId: WrapperId, address: Option[Address] = None): Wrapper = {
        val config = new WrapperConfiguration(WrapperId.idToPort(wrapperId) :: Nil, address)
        wrapperId match {
            case WrapperId.Flask => new FlaskWrapper(config)
            case WrapperId.Postgres => new PostgresWrapper(config)
            case WrapperId.Dummy => new DummyWrapperImplementation(config)
        }
    }
}
