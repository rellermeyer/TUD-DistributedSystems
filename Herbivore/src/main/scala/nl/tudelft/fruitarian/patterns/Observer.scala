package nl.tudelft.fruitarian.patterns

/** Observer for a given type of events.
 * Combined with the Subscriber.
 * @tparam S The type of objects to expect updates about.
 */
trait Observer[S] {
  def receiveUpdate(event: S): Unit
}
