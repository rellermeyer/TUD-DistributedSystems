package common

import java.rmi.Remote

// Used by the Evaluator trait.
sealed abstract class AResult(value: Int, scalarClock: Int) extends Remote with java.io.Serializable
case class Result(value: Int, scalarClock: Int) extends AResult(value, scalarClock)
