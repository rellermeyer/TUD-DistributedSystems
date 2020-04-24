package example

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

class AActor(actorRef: ActorRef) extends Actor {
  val sleepTime = 500

  val BActorRef: ActorRef = actorRef
  var ACount: Int = 0

  override def receive: Receive = {
    case "A exit" =>
      println("A exit!")
      context.stop(self)
      context.system.terminate()
    case "start" =>
      println("A start!")
      self ! "Send to A!"
    case "Send to A!" =>
      // send to B
      // need BActorRef
      ACount += 1
      if (ACount == 5) {
        self ! "A exit"
      }
      println("A => B")
      Thread.sleep(sleepTime)
      BActorRef ! "B runs"
  }
}

class BActor extends Actor {
  val sleepTime = 500

  var BCount: Int = 0

  override def receive: Receive = {
    case "B exit" =>
      println("B exit!")
      context.stop(self)
      context.system.terminate()
    case "B runs" =>
      BCount += 1
      println("B => A")
      sender() ! "Send to A!"
      Thread.sleep(sleepTime)
      if (BCount == 4) {
        self ! "B exit"
      }
  }
}

object demo8_actorCommunication {
  private val factory = ActorSystem("actorfactory")
  // B referred to A
  private val BActorRef = factory.actorOf(Props[BActor], "bActor")
  private val AActorRef = factory.actorOf(Props(new AActor(BActorRef)), "aActor")

  def main(args: Array[String]): Unit = {
    AActorRef ! "start"
  }
}
