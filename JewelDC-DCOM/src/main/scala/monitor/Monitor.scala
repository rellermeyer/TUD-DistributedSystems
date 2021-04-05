package monitor

import common._

/**
  * A monitor in which expressions can be entered and the result is returned, together with a complete sorted Event Log
  * of the activity.
  */
class Monitor(remoteEvaluator: RemoteEvaluator) {

  // Used to generate names for the activity.
  var activityNum: Int = 0

  /**
    * Evaluates an expression and gives back the event log of the expression.
    * @param expression The expression to evaluate.
    * @return A pair (value, eventLog), where the value is the result of the expression and the event log is a sorted
    *         list of events.
    */
  def monitorAndEvaluate(expression: Expression): (Int, List[Event]) = {
    val activity = "Activity" + activityNum
    activityNum += 1

    // Evaluating the expression.
    val Result(value, _) = remoteEvaluator.evaluateExpression(activity, expression)

    // Getting the events from the monitor.
    val monitorEvents = remoteEvaluator.flushEventBuffer(activity).toList

    // Getting all other events.
    val allEvents = getAllEvents(activity, monitorEvents, List("Monitor"))

    // Sorting the events by their scalar clock.
    val sortedEvents = allEvents.sortWith((ev1, ev2) => ev1.getScalarClock < ev2.getScalarClock)

    (value, sortedEvents)
  }

  // Algorithm for getting all of the events recursively.
  // The way it works is by checking all currently gotten events for outgoing calls. For every outgoing calls, gets
  // the events of the client that gets called and add them to the event list. This gets repeated until no new clients
  // gets visited.
  private def getAllEvents(activity: String, currentEvents: List[Event], visitedNodes: List[String]): List[Event] = {
    currentEvents match {
      case Nil => Nil
      case event :: tail => event match {
        case OutgoingCall(_, calleeId, _) => {
          if (visitedNodes.contains(calleeId)) {
            event :: getAllEvents(activity, tail, visitedNodes)
          } else {
            val evaluator = remoteEvaluator.getRegistry.lookup(calleeId).asInstanceOf[Evaluator]
            event :: getAllEvents(activity, tail ++ evaluator.flushEventBuffer(activity).toList, calleeId :: visitedNodes)
          }
        }
        case _ => event :: getAllEvents(activity, tail, visitedNodes)
      }
    }
  }

}
