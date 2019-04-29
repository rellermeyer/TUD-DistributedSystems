package main.scala.tact.conit

/**
  * Conit class constructor
  *
  * @param key            The key the conit keeps track of
  * @param value          The value currently known in the track
  * @param numericBound   The maximum numeric error. Will be randomly generated if not given
  * @param orderBound     The maximum order error. Will be randomly generated if not given
  * @param stalenessBound The maximum staleness error. Will be randomly generated if not given
  */
class Conit(val key: Char, var value: Int, var numericBound: Int, var orderBound: Int, var stalenessBound: Int) {

  /**
    * Secondary Conit class constructor
    * Will randomly generate numericBound, orderBound, stalenessBound between 1 and 10
    *
    * @param key The key the conit keeps track of
    * @param va  The value currently known in the track
    */
  def this(key: Char, va: Int) {
    this(key, va, 0, 0, 0)
    randomizeBounds()
  }

  /**
    * Updates the value of conit "key" by adding "va"
    *
    * @param key the name of the conit that has te be updated
    * @param va  the value that has to be added to the conit
    * @return an option containing an integer representing the new value of the conit if it equals the name of the conit
    *         None if it does not equal the name of the conit
    */
  def update(key: Char, va: Int): Option[Int] = {
    if (key.equals(key)) {
      value += va
      return Some(value)
    }
    None
  }

  /**
    * Updates the value of the current conit by adding "va"
    *
    * @param va the value that has to be added to the conit
    * @return an option containing an integer representing the new value of the conit
    */
  def update(va: Int): Int = {
    value += va
    value
  }

  /**
    * Randomizes error bounds in the conit
    */
  def randomizeBounds(): Unit = {
    val random = new scala.util.Random
  }
}
