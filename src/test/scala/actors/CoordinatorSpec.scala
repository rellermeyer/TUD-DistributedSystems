package actors

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import util.Messages._

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "A coordinator" must {
    "reply to greeted" in {
      val replyProbe = createTestProbe[Greeted]()
      val underTest = spawn(Coordinator())
      underTest ! Greet("Santa", replyProbe.ref)
      replyProbe.expectMessage(Greeted("Santa", underTest.ref))
    }
  }
}
