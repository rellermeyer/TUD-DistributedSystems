package nl.tudelft.fruitarian.patterns

/**
 * Subject that that Observers can subscribe to.
 * Events can then be sent to this subject which takes care of notifying all
 * observers.
 * @tparam S The type of events that will be broadcasted in this subject.
 */
trait Subject[S] {
  // List of observers listening to updates.
  private var observers: List[Observer[S]] = Nil

  /** Add observer to this topic.
   * @param observer The observer that wants to listen to these events.
   */
  def addObserver(observer: Observer[S]): Unit =
    observers = observer :: observers

  /** Notify all observers about a given event.
   * @param event The event to send out to all observers.
   */
  def notifyObservers(event: S): Unit =
    observers.foreach(_.receiveUpdate(event))
}
