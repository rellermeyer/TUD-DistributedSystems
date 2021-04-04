package common

import java.rmi.{Remote, RemoteException}

/**
  * Used to represent objects that are able to evaluate a specific binary expression consisting of two subexpressions
  * into an integer.
  *
  * Usable in Java RMI.
  */
trait Evaluator extends Remote {

  /**
    * Does a computation on based on two different Expressions. What the computation does is entirely up to the
    * implementation of this trait.
    *
    * Because this method can be called remotely and Evaluators are monitorable, it is required to specify the name of
    * the activity that corresponds to this evaluation. Furthermore, a Scalar Clock needs to be passed which represents
    * the current step in the activity.
    *
    * @param activity The name of the activity to which this evaluation corresponds to.
    * @param left The left expression.
    * @param right The right expression.
    * @param scalarClock A scalar clock representing the current step in the activity.
    * @throws java.rmi.RemoteException Whenever something goes wrong when this method is called remotely.
    * @return The computed resulting integer based on the two subexpressions and a new updated scalar clock which has
    *         been incremented for every event that occured. Note that a "AResult" is used to return, rather than a
    *         Tuple. This is because tuples are part of the Scala library and therefore not serializable with Java RMI.
    */
  @throws(classOf[RemoteException])
  def evaluateBinaryExpression(activity: String,
                               left: Expression,
                               right: Expression,
                               scalarClock: Int): AResult

  /**
    * Gets and removes all events for a given Activity.
    *
    * @param activity The activity for which to get and remove the event buffer.
    * @throws java.rmi.RemoteException Whenever something goes wrong when this method is called remotely.
    * @return An array containing all the events that occured for the given activity.
    */
  @throws(classOf[RemoteException])
  def flushEventBuffer(activity: String): Array[Event]

  // Used for experimentation, also see evaluateBinaryExpression/4
  @throws(classOf[RemoteException])
  def evaluateBinaryExpressionUnmonitored(left: Expression, right: Expression): Int

}
