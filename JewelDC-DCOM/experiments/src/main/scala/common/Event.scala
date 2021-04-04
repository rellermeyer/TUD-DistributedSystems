package common

import java.rmi.Remote

/**
  * Represents an Object-Related Event. This includes outgoing calls, incoming calls, outgoing replies, and incoming
  * replies.
  *
  * @param scalarClock The time at which this Event occurred.
  */
sealed abstract class Event(scalarClock: Int) extends Remote with java.io.Serializable {
  def getScalarClock: Int = scalarClock
}
case class OutgoingCall(callerId: String, calleeId: String, timestamp: Int) extends Event(timestamp)
// Note that an incoming call does not have a callerId, this is due to a method not knowning who called it. The only
// way to circumvent this is to pass the callerId as argument, but that seemed unnecessary.
case class IncomingCall(calleeId: String, timestamp: Int) extends Event(timestamp)
// Similarly to an incoming call, the name of the original caller is unknown.
case class OutgoingReply(calleeId: String, timestamp: Int) extends Event(timestamp)
case class IncomingReply(callerId: String, calleeId: String, timestamp: Int) extends Event(timestamp)
