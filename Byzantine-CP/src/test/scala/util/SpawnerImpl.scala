package util

import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.{ActorRef, Behavior}

class SpawnerActorTestKitImpl[T](context: ActorTestKit) extends Spawner() {
  override def spawn[K](behavior: Behavior[K], name: String): ActorRef[K] = context.spawn(behavior, name)
}

class SpawnerScalaTestWithActorTestKitImpl[T](context: ScalaTestWithActorTestKit) extends Spawner() {
  override def spawn[K](behavior: Behavior[K], name: String): ActorRef[K] = context.spawn(behavior, name)
}
