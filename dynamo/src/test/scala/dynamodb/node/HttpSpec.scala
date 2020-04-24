package dynamodb.node

import akka.actor
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.VectorClock
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import dynamodb.node.ValueRepository.{KO, OK, Value}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HttpSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalatestRouteTest {
  import dynamodb.node.JsonSupport._

  lazy val testKit: ActorTestKit = ActorTestKit()

  implicit def typedSystem: ActorSystem[Nothing] = testKit.system

  override def createActorSystem(): actor.ActorSystem = testKit.system.toClassic

  implicit val valueRepository: ActorRef[ValueRepository.Command] = testKit.spawn(ValueRepository(""))
  implicit val dht: ActorRef[DistributedHashTable.Command] = testKit.spawn(DistributedHashTable())
  val internalClient: ActorRef[InternalClient.Command] = testKit.spawn(InternalClient("", 0, 4, 3, 2, ""))

  lazy val routes: Route = new ExternalRoutes(valueRepository, internalClient).theValueRoutes

  "The service" should {
    "return a 404 when item does not exist" in {
      Get("/entity") ~> Route.seal(routes) ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "create an item" in {
      val mockedBehavior = Behaviors.receiveMessage[InternalClient.Command] {
        case InternalClient.Put(_, replyTo) =>
          replyTo ! InternalClient.OK
          Behaviors.same
      }
      val valueRepository = testKit.spawn(ValueRepository(""))
      val internalProbe = testKit.createTestProbe[InternalClient.Command]()
      val mockedInternalClient = testKit.spawn(Behaviors.monitor(internalProbe.ref, mockedBehavior))
      val routes = new ExternalRoutes(valueRepository, mockedInternalClient).theValueRoutes
      Post("/values", Value("myKey", "myVal")) ~> routes ~> check {
        responseAs[String] shouldEqual "Value added"
      }
    }

    "retrieve created item" in {
      val mockedBehavior = Behaviors.receiveMessage[InternalClient.Command] {
        case InternalClient.Get("myKey", replyTo) =>
          replyTo ! InternalClient.ValueRes(Value("myKey", "myValue"))
          Behaviors.same
      }
      val valueRepository = testKit.spawn(ValueRepository(""))
      val internalProbe = testKit.createTestProbe[InternalClient.Command]()
      val mockedInternalClient = testKit.spawn(Behaviors.monitor(internalProbe.ref, mockedBehavior))
      val routes = new ExternalRoutes(valueRepository, mockedInternalClient).theValueRoutes

      Get("/values/myKey") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}
