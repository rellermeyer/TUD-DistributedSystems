package core.communication

import core.messages.Message
import core.Node

import scala.collection.mutable

abstract class CommunicationLayer(owner: Node) extends Runnable {
  val outgoingQueue: mutable.Queue[Message] = mutable.Queue[Message]()
  protected var finished = false
  protected val waitTime = 100

  def init(): Unit

  def sendMessage(msg: Message): Unit = {
    outgoingQueue.enqueue(msg)
  }

  def receiveMessage(msg: Message): Unit = {
    owner.handleMessage(msg)
  }

  def stop(): Unit = {
    this.finished = true
  }

  def run(): Unit
}
