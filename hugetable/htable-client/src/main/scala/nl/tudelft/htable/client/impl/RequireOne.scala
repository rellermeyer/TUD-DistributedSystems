package nl.tudelft.htable.client.impl

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

/**
 * An Akka [GraphStage] that requires at least one element to pass through the graph or that the
 * upstream failed.
 */
class RequireOne[A](val exception: () => Exception) extends GraphStage[FlowShape[A, A]] {
  val in: Inlet[A] = Inlet[A]("RequireOne.in")
  val out: Outlet[A] = Outlet[A]("RequireOne.out")

  override val shape: FlowShape[A, A] = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      private var hasSent = false

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            hasSent = true
            push(out, grab(in))
          }

          override def onUpstreamFinish(): Unit = {
            if (!hasSent) {
              failStage(exception())
            }

            super.onUpstreamFinish()
          }
        }
      )
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}
