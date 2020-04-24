package Beehive

import scala.collection.mutable

trait HTTPMessage {
  def getType(): String
}

class HTTPReplicateMessage(requestID: Int, set: mutable.Set[DataItem]) extends HTTPMessage {
  val req: Int = requestID
  val s: mutable.Set[DataItem] = set

  def getType(): String = {
    "replicate"
  }

  def getRequestID(): Int = {
    req
  }

  def getSet(): mutable.Set[DataItem] = {
    s
  }
}

class HTTPRemoveMessage(removeSet: mutable.Set[Int]) extends HTTPMessage {
  val r: mutable.Set[Int] = removeSet

  def getType(): String = {
    "remove"
  }

  def getRemoveSet(): mutable.Set[Int] = {
    r
  }
}

class HTTPRequest(itemID: String, reqID: Int, hopCount: Int) extends HTTPMessage {
  val iid: String = itemID
  val rid: Int = reqID
  val hc: Int = hopCount

  def getType(): String = {
    "request"
  }

  def getItemID(): String = {
    iid
  }

  def getReqID(): Int = {
    rid
  }

  def getHopCound(): Int = {
    hc
  }
}

