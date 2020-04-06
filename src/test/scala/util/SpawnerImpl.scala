package util

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.{ActorRef, Behavior}

class SpawnerTestImpl[T](context: ScalaTestWithActorTestKit) extends Spawner() {
  override def spawn[K](behavior: Behavior[K], name: String): ActorRef[K] = context.spawn(behavior, name)
}
