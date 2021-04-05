package subtractor

import java.rmi.registry.Registry

import common._

/**
  * An evaluator that subtracts the result of the two subexpressions.
  */
class Subtractor(registry: Registry) extends AbstractEvaluator(registry) {
  override protected def evaluate(left: Int, right: Int): Int = left - right

  override protected def getName: String = "Subtractor"
}
