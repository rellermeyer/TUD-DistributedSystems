package monitor

import common._

import scala.collection.mutable

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
    * @return A pair (value, eventLog, endTime), where the value is the result of the expression and the event log is a
    *         sorted list of events. The endTime is a value used for experimentation and it denotes the timestamp
    *         at which the value has been calculated, so this does not include the phase where events are fetched and
    *         sorted.
    */
  def monitorAndEvaluate(expression: Expression): (Int, List[Event], Long) = {
    val activity = "Activity" + activityNum
    activityNum += 1

    // Evaluating the expression.
    val Result(value, _) = remoteEvaluator.evaluateExpression(activity, expression)

    val endTime = System.currentTimeMillis()

    // Getting all other events.
    val allEvents = getAllEvents(activity)

    // Sorting the events by their scalar clock.
    val sortedEvents = allEvents.sortWith((ev1, ev2) => ev1.getScalarClock < ev2.getScalarClock)

    (value, sortedEvents, endTime)
  }

  private def getAllEvents(activity: String): List[Event] = {
    var events = mutable.MutableList[Event]()
    var uncheckedEvents = mutable.Queue[Event]()
    uncheckedEvents ++= remoteEvaluator.flushEventBuffer(activity)
    var visited = Set("Monitor")

    while (uncheckedEvents.nonEmpty) {
      val event = uncheckedEvents.dequeue()
      events += event
      if (event.isInstanceOf[OutgoingCall]) {
        val OutgoingCall(_, callee, _) = event
        if (!visited.contains(callee)) {
          visited += callee
          val evaluator = remoteEvaluator.getRegistry.lookup(callee).asInstanceOf[Evaluator]
          uncheckedEvents ++= evaluator.flushEventBuffer(activity)
        }
      }
    }

    events.toList
  }

}
