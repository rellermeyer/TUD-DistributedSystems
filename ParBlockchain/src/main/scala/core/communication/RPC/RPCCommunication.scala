package core.communication.RPC

import core.communication.CommunicationLayer
import akka.actor.{ActorSystem, Props}
import core.Node

class RPCCommunication(owner: Node) extends CommunicationLayer(owner) {
  private val factory = ActorSystem("actorfactory")
  // B referred to A
  private val actor = factory.actorOf(Props(new ActorNode(this)), owner.id)

  override def init(): Unit = {
    ActorLookup.addNode(owner, actor)
  }

  override def run(): Unit = {
    while (!finished) {
      while (outgoingQueue.nonEmpty) {
        val m = outgoingQueue.dequeue()
        val ref = ActorLookup.lookupNode(m.receiver).get
        ref ! m
      }
      Thread.sleep(waitTime)
    }
  }
}
