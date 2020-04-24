package core.communication.RPC

import core.messages.Message
import akka.actor.Actor
import scala.concurrent._
import ExecutionContext.Implicits.global

import scala.concurrent.Future

class ActorNode(owner: RPCCommunication) extends Actor {
  override def receive: Receive = {
    case m: Message => Future {owner.receiveMessage(m)}
    case _ => println("Received garbage")
  }
}
