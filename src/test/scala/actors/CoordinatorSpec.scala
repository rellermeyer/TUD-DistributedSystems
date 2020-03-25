package actors

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import java.security.{KeyPair, KeyPairGenerator, PrivateKey, PublicKey}

import util.Messages
import util.Messages._

import scala.collection.mutable

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  var testNr = 0
  "A transaction" must {
    "succeed with 1 coordinator and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 4 coordinators and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 1 coordinator and 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").withOccurrences(4).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "abort with 1 coordinator and 1 participant (participant should never be asked to prepare)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").expect {
        cs.foreach(c => c ! Messages.InitAbort(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "abort with 4 coordinators and 1 participant (participant should never be asked to prepare)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").expect {
        cs.foreach(c => c ! Messages.InitAbort(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "abort with 1 coordinator and 4 participants (participant should never be asked to prepare)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        cs.foreach(c => c ! Messages.InitAbort(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }
  "A participant" must {
    "be able to abort a transaction" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 0, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to unilaterally abort a transaction (1 coordinator)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(5).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to unilaterally abort a transaction (4 coordinators)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(5).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }
  "2 transactions" must {
    "succeed" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t0 = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t0))
      val t1 = Transaction(1)
      ps.foreach(p => p ! PropagateTransaction(t1))
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t0.id, p))
      }
      LoggingTestKit.info("Committed transaction 1").expect {
        cs.foreach(c => c ! Messages.InitCommit(t1.id, p))
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  def spawnAll(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0): (Array[Messages.Coordinator], Array[Messages.Participant]) = {
    val cs = new Array[Messages.Coordinator](nCoordinators)

    //
    var kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)

    var masterKey = kpg.generateKeyPair
    var keyPairs = for (i <- 1 to nCoordinators + nAbortingParticipants + nCommittingParticipants) yield kpg.generateKeyPair


    var pubKeys: PubKeys = mutable.Map()
    var typeCounter: Int = 0
    var actorCounter: Int = 0

    for (e <- keyPairs) {
      var pubKey = e.getPublic()
      var signature = sign(pubKey.toString(), masterKey.getPrivate())
      pubKeys += (typeCounter, actorCounter) -> (pubKey, signature)

      actorCounter += 1
      if (typeCounter == 0 && actorCounter == nCoordinators) {
        typeCounter += 1
        actorCounter = 0
      }
    }


    //

    for (x <- 0 until nCoordinators) {
      cs(x) = spawn(Coordinator(keyPairs(x).getPrivate(), pubKeys, masterKey.getPublic()), testNr + "Coordinator-" + x)
    }
    cs.foreach { x => x ! Messages.Setup(cs) }
    val ps = new Array[Messages.Participant](nCommittingParticipants + nAbortingParticipants)
    for (x <- 0 until nCommittingParticipants) {
      ps(x) = spawn(Participant(cs, Decision.COMMIT, keyPairs(nCoordinators + x).getPrivate(), pubKeys, masterKey.getPublic()), testNr + "Participant-" + x)
    }
    for (x <- nCommittingParticipants until nCommittingParticipants + nAbortingParticipants) {
      ps(x) = spawn(Participant(cs, Decision.ABORT, keyPairs(nCoordinators + nCommittingParticipants + x).getPrivate(), pubKeys, masterKey.getPublic()), testNr + "Participant-" + x)
    }
    (cs, ps)
  }


}
