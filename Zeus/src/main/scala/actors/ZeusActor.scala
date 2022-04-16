package actors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import datatypes.ZeusDataObject.ZeusDataObject

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object ZeusActor {

  final case class OwnershipRequester(resKey: String, from: ActorRef[OwnershipResponder])
  // Pass resource as well?
  final case class OwnershipResponder(ownershipGiven: Boolean, to: ActorRef[OwnershipRequester])

  val ds: mutable.HashMap[String, ZeusDataObject] = new mutable.HashMap()

  // Listen to requests for resource ownership
  def apply(): Behavior[OwnershipRequester] = Behaviors.receive { (context, message) =>

    // Check resource requested
    context.log.info("Requested resource {}!", message.resKey)

    // If owner of resource
    if (ds.contains(message.resKey)) {
      // TODO Make current user no longer owner and pass data.
      message.from ! OwnershipResponder(ownershipGiven = true, context.self)
    } else {
      message.from ! OwnershipResponder(ownershipGiven = false, context.self)
    }
    Behaviors.same
  }

//#zeus-bot
object ZeusActorBot {

  def apply(max: Int): Behavior[ZeusActor.OwnershipResponder] = {
    bot(0, max)
  }

  private def bot(greetingCounter: Int, max: Int): Behavior[ZeusActor.OwnershipResponder] =
    Behaviors.receive { (context, message) =>
      val n = greetingCounter + 1
      context.log.info("Greeting {} for {}", n, message.to)
      if (n == max) {
        Behaviors.stopped
      } else {
        // TODO Request resource.
        message.to ! ZeusActor.OwnershipRequester("Test" + n, context.self)
        // TODO Handle response.
        bot(n, max)
      }
    }
}
//#zeus-bot

//#zeus-main
object ZeusActorMain {

  final case class SayHello(name: String)

  val rActors: ListBuffer[ActorRef[ZeusActor.OwnershipResponder]] = ListBuffer[ActorRef[ZeusActor.OwnershipResponder]]()

  def apply(): Behavior[SayHello] =
    Behaviors.setup { context =>
      //#create-actors
      val zeus = context.spawn(ZeusActor(), "greeter")
      //#create-actors

      Behaviors.receiveMessage { message =>

        for (i <- 0 to 9) {
          context.log.info("spawn actor: " + i)
          val rAct = context.spawn(ZeusActorBot(max = 10), "act" + i)
          rActors.addOne(rAct)
        }
        rActors.foreach(x => zeus ! ZeusActor.OwnershipRequester(message.name, x))
        Behaviors.same
      }
    }
}
}