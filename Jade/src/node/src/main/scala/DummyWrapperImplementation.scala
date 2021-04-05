import ComponentState.ComponentState

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * A dummy implementation of a wrapper.
  */
class DummyWrapperImplementation(wrapperConfiguration: WrapperConfiguration) extends Wrapper(wrapperConfiguration) {
    override def stop(): Unit = ???

    override def heartbeat(): Future[ComponentState] = Future { ComponentState.Running }
}
