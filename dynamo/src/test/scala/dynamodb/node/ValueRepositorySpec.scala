package dynamodb.node

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.VectorClock
import akka.util.Timeout
import dynamodb.node.ValueRepository._
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable.TreeMap
import scala.concurrent.Await.result
import scala.concurrent.duration._
import scala.language.postfixOps

class ValueRepositorySpec extends AnyFlatSpec with Matchers with BeforeAndAfter {
  val testKit: ActorTestKit = ActorTestKit()

  implicit val system: ActorSystem[Nothing] = testKit.system
  implicit val timeout: Timeout = testKit.timeout

  import akka.actor.typed.scaladsl.AskPattern._

  it should "add a value" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "myValue"), _: ActorRef[Response])), 1 second) should be(OK)
  }

  it should "read an empty value" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(GetValueByKey("myKey", _: ActorRef[Option[Value]])), 1 second) should be(None)
  }

  it should "read an added value" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "myValue", new VectorClock(TreeMap("testName" -> 0))), _: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(GetValueByKey("myKey", _: ActorRef[Option[Value]])), 1 second) should be(Option(Value("myKey", "myValue", new VectorClock(TreeMap("testName" -> 0)))))
  }

  it should "remove a value" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "myValue"), _: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(RemoveValue("myKey", _: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(GetValueByKey("myKey", _: ActorRef[Option[Value]])), 1 second) should be(None)
  }

  it should "return an error when removing a value if it does not extist" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(RemoveValue("myKey", _: ActorRef[Response])), 1 second) should be(KO("Not Found"))
  }

  it should "update a value if the version is newer" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "myOtherValue"), _: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(AddValue(Value("myKey", "myOtherValue", new VectorClock(TreeMap("dynamodb.node.ValueRepositorySpec" -> 0, "MyNode" -> 1))), _: ActorRef[Response])), 1 second) should be(OK)
  }

  it should "not update a value if the version is equal" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "myOtherValue", new VectorClock(TreeMap("dynamodb.node.ValueRepositorySpec" -> 2))), _: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(AddValue(Value("myKey", "myAnotherValue", new VectorClock(TreeMap("dynamodb.node.ValueRepositorySpec" -> 2))), _: ActorRef[Response])), 1 second) should be(KO("Version too old"))
  }

  it should "not update a value if the version is older" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "value 1"), _: ActorRef[Response])), 1 second) should be(OK)
    result(valueRepository.ask(AddValue(Value("myKey", "value 2", new VectorClock(TreeMap("dynamodb.node.ValueRepositorySpec" -> 1))), _: ActorRef[Response])), 1 second) should be(OK)
    result(valueRepository.ask(AddValue(Value("myKey", "value 3", new VectorClock(TreeMap("dynamodb.node.ValueRepositorySpec" -> 0))), _: ActorRef[Response])), 1 second) should be(KO("Version too old"))

    result(valueRepository.ask(GetValueByKey("myKey", _: ActorRef[Option[Value]])), 1 second) should be (Some(Value("myKey", "value 2", new VectorClock(TreeMap("dynamodb.node.ValueRepositorySpec" -> 1)))))
  }

  it should "clear the repository" in {
    val valueRepository = testKit.spawn(ValueRepository("dynamodb.node.ValueRepositorySpec"))

    result(valueRepository.ask(AddValue(Value("myKey", "myValue"), _: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(ClearValues(_: ActorRef[Response])), 1 second) should be(OK)

    result(valueRepository.ask(GetValueByKey("myKey", _: ActorRef[Option[Value]])), 1 second) should be(None)
  }
}
