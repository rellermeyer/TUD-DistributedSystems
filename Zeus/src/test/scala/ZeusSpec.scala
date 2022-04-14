import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import ZeusSuperVisor.{OwnershipMsg, OwnershipRequest, OwnershipResponse, ReporterMsg, BroadcastTransactions, ReportOSRequest}
import ZeusActor._
import datatypes.ZeusDataObject.ZeusDataObject
import ZeusSuperVisor._
import scala.concurrent._
import scala.concurrent.duration._//#definition
import ZeusTaskManager._
class ZeusSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  //#definition

  "Test 1" must {
    //#test
    "receive correct ownership response" in {
              val replyProbe = createTestProbe[OwnershipMsg]()
              val underTest = spawn(ZeusSuperVisor())
              underTest ! OwnershipRequest("DATAKEY2", replyProbe.ref)
              replyProbe.expectMessage(OwnershipResponse("DATAKEY2", ZeusDataObject("Valid",1,"C") , underTest.ref))
    }
    //#test
  }
    "Test 2" must {
    //#test
    "receive correct ownership response" in {
              val replyProbe = createTestProbe[OwnershipMsg]()
              val underTest = spawn(ZeusSuperVisor())
              underTest ! OwnershipRequest("DATAKEY5", replyProbe.ref)
              replyProbe.expectMessage(OwnershipResponse("DATAKEY5", ZeusDataObject("Valid",1,"F") , underTest.ref))
    }
    //#test
  }
  "Test 3" must {
    //#test
    "Receive no message - wrong datakey" in {
              val replyProbe = createTestProbe[OwnershipMsg]()
              val underTest = spawn(ZeusSuperVisor())
              underTest ! OwnershipRequest("DATAKEY11", replyProbe.ref)
              replyProbe.expectNoMessage()
    }
    //#test
  }
"Test 4" must {
    //#test
    "reply to greeted" in {
              val replyProbe = createTestProbe[OwnershipMsg]()
              val underTest = spawn(ZeusSuperVisor())
              underTest ! OwnershipRequest("DATAKEY0", replyProbe.ref)
              replyProbe.expectMessage(OwnershipResponse("DATAKEY0", ZeusDataObject("Valid",1,"A") , underTest.ref))
    }
    //#test
  }
"Test 5" must {
    //#test
    "Reporter must receive OS request" in {
              val replyProbe = createTestProbe[OwnershipMsg]()
              val reporterProbe = createTestProbe[ReporterMsg]()
              val underTest = spawn(ZeusSuperVisor())
              underTest ! OwnershipRequest("DATAKEY0", replyProbe.ref)
              reporterProbe ! ReportOSRequest()
              reporterProbe.expectMessage(ReportOSRequest())

    }
    //#test
  }
  "Test 5" must {
    //#test
    "Reporter must receive locked data status" in {
              val replyProbe = createTestProbe[OwnershipMsg]()
              val reporterProbe = createTestProbe[ReporterMsg]()
              val underTest = spawn(ZeusSuperVisor())
              underTest ! OwnershipRequest("DATAKEY0", replyProbe.ref)
              reporterProbe ! ReportLockedData()
              reporterProbe.expectMessage(ReportLockedData())

    }
    //#test
  }

}
//#full-example