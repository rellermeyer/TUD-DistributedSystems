import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class NodeTest extends TestKit(ActorSystem("NodeTest")) with ImplicitSender
    with WordSpecLike with Matchers with BeforeAndAfterAll {

    var node: Node = _
    var actorRef: ActorRef = _

    override def beforeAll: Unit = {
        actorRef = TestActorRef.create[Node](system, Props(new Node()))
    }

    override def afterAll: Unit = {
        TestKit.shutdownActorSystem(system)
    }

    "A Node" must {
        "receive a heartbeat response" in {
            actorRef ! Heartbeat()
            val response = expectMsgClass(classOf[HeartbeatResponse])
            assertResult(response.nodeState)(ComponentState.Running)
        }
    }
}
