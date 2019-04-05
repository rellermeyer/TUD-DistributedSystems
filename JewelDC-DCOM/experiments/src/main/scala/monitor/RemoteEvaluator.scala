package monitor

import java.rmi.registry.Registry

import common.{AResult, AbstractEvaluator, Event, Expression}

import scala.collection.mutable

/**
  * A special evaluator that does not actually evaluate anything, but rather just takes the remote parts of the
  * abstract evaluator and works with them :)
  */
class RemoteEvaluator(registry: Registry) extends AbstractEvaluator(registry) {

  /**
    * Remotely evaluates any expression.
    *
    * @param activity The activity to which this evaluation belongs.
    * @param expression The expression to evaluate.
    * @return
    */
  def evaluateExpression(activity: String, expression: Expression): AResult = {
    val eventBuffer = new mutable.MutableList[Event]
    eventBuffers.put(activity, eventBuffer)
    super.evaluateExpressionRemotely(activity, expression, 0, eventBuffer)
  }

  def getRegistry: Registry = registry

  override protected def evaluate(left: Int, right: Int): Int = throw new RuntimeException()

  override protected def getName: String = "Monitor"

}
