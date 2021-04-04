package multiplier

import java.rmi.registry.Registry

import common._

/**
  * An evaluator that multiplies the result of the two subexpressions.
  */
class Multiplier(registry: Registry) extends AbstractEvaluator(registry) {
  override protected def evaluate(left: Int, right: Int): Int = left * right

  override protected def getName: String = "Multiplier"
}
