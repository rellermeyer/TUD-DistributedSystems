package common

import java.rmi.RemoteException
import java.rmi.registry.Registry
import java.util

import scala.collection.mutable

/**
  * Provides a base implementation for the Evaluator trait. This creatly simplies the implementation required for
  * subclasses.
  */
abstract class AbstractEvaluator(registry: Registry,
                                 val eventBuffers: util.HashMap[String, mutable.MutableList[Event]] = new util.HashMap[String, mutable.MutableList[Event]]()) extends Evaluator {

  def evaluateExpressionRemotelyUnmonitored(exp: Expression): Int = {
    exp match {
      case NumberLiteral(number) => number
      case BinaryExpression(operator, left, right) => operator match {
        case PlusOp() => adder().evaluateBinaryExpressionUnmonitored(left, right)
        case MinusOp() => subtractor().evaluateBinaryExpressionUnmonitored(left, right)
        case MultOp() => multiplier().evaluateBinaryExpressionUnmonitored(left, right)
      }
    }
  }

  override def evaluateBinaryExpression(activity: String,
                               left: Expression,
                               right: Expression,
                               scalarClock: Int): AResult = {
    var eventBuffer = eventBuffers.get(activity)
    if (eventBuffer == null) {
      eventBuffer = new mutable.MutableList[Event]()
      eventBuffers.put(activity, eventBuffer)
    }

    // Log that we received a call.
    eventBuffer += IncomingCall(getName, scalarClock + 1)

    // Evaluate the left subexpression.
    val Result(leftValue, scalarClock2) = evaluateExpressionRemotely(activity, left, scalarClock + 1, eventBuffer)
    // Evaluate the right subexpression.
    val Result(rightValue, scalarClock3) = evaluateExpressionRemotely(activity, right, scalarClock2, eventBuffer)

    // Log that we are going to return.
    eventBuffer += OutgoingReply(getName, scalarClock3 + 1)

    Result(evaluate(leftValue, rightValue), scalarClock3 + 1)
  }

  override def flushEventBuffer(activity: String): Array[Event] = {
    val result = eventBuffers.get(activity)
    eventBuffers.remove(activity)
    result match {
      case null => throw new RemoteException("No activity with name \"" + activity + "\" that name logged on " + getName)
      case r => r.toArray
    }
  }

  /**
    * Evaluates a single expression by checking what type it is and calling the appropriate evaluator sitting somewhere
    * on the RMI registry. Number Literals are not evaluted remotely.
    *
    * @param activity The name of the activity to which this evaluation corresponds to.
    * @param expression The expression to evaluate.
    * @param scalarClock A scalar clock representing the current step in the activity.
    * @param eventBuffer The event buffer on which event logs need to be written to.
    * @return The computed resulting integer based on the two subexpressions and a new updated scalar clock which has
    *         been incremented for every event that occured.
    */
  protected def evaluateExpressionRemotely(activity:String,
                                           expression: Expression,
                                           scalarClock: Int,
                                           eventBuffer: mutable.MutableList[Event]): AResult = {
    expression match {
      case NumberLiteral(number) => Result(number, scalarClock)
      case BinaryExpression(operator, left, right) => operator match {
        case PlusOp() =>
          eventBuffer += OutgoingCall(getName, "Adder", scalarClock + 1)
          val Result(value, newScalarClock) = adder().evaluateBinaryExpression(activity, left, right, scalarClock + 1)
          eventBuffer += IncomingReply(getName, "Adder", newScalarClock + 1)
          Result(value, newScalarClock + 1)
        case MinusOp() =>
          eventBuffer += OutgoingCall(getName, "Subtractor", scalarClock + 1)
          val Result(value, newScalarClock) = subtractor().evaluateBinaryExpression(activity, left, right, scalarClock + 1)
          eventBuffer += IncomingReply(getName, "Subtractor", newScalarClock + 1)
          Result(value, newScalarClock + 1)
        case MultOp() =>
          eventBuffer += OutgoingCall(getName, "Multiplier", scalarClock + 1)
          val Result(value, newScalarClock) = multiplier().evaluateBinaryExpression(activity, left, right, scalarClock + 1)
          eventBuffer += IncomingReply(getName, "Multiplier", newScalarClock + 1)
          Result(value, newScalarClock + 1)
      }
    }
  }

  /**
    * (ABSTRACT) Evalutes the expression as if it were consisting of two number literals.
    *
    * @param left The left value.
    * @param right The right value.
    * @return The resulting evaluation.
    */
  protected def evaluate(left: Int, right: Int): Int

  /**
    * (ABSTRACT) Gets the name of this Evaluator which corresponds to its name on the RMI registry.
    * @return The name.
    */
  protected def getName: String

  private var cachedAdder: Option[Evaluator] = None
  private var cachedSubtractor: Option[Evaluator] = None
  private var cachedMultiplier: Option[Evaluator] = None

  /**
    * Creates or gets an evaluator stub that can add two expressions. Fails if there doesn't exist an "Adder" evaluator
    * on the registry.
    *
    * @return the adder stub.
    */
  private def adder(): Evaluator = cachedAdder match {
    case None =>
      val result = registry.lookup("Adder").asInstanceOf[Evaluator]
      cachedAdder = Some(result)
      result
    case Some(result) => result
  }

  /**
    * Creates or gets an evaluator stub that can subtract two expressions. Fails if there doesn't exist an "Subtractor"
    * evaluator on the registry.
    *
    * @return the subtractor stub.
    */
  private def subtractor(): Evaluator = cachedSubtractor match {
    case None =>
      val result = registry.lookup("Subtractor").asInstanceOf[Evaluator]
      cachedSubtractor = Some(result)
      result
    case Some(result) => result
  }

  /**
    * Creates or gets an evaluator stub that can multiply two expressions. Fails if there doesn't exist an "Multiplier"
    * evaluator on the registry.
    *
    * @return the multiplier stub.
    */
  private def multiplier(): Evaluator = cachedMultiplier match {
    case None =>
      val result = registry.lookup("Multiplier").asInstanceOf[Evaluator]
      cachedMultiplier = Some(result)
      result
    case Some(result) => result
  }

  def evaluateBinaryExpressionUnmonitored(left: Expression, right: Expression): Int =
    evaluate(evaluateExpressionRemotelyUnmonitored(left), evaluateExpressionRemotelyUnmonitored(right))

}
