package actors

import java.security.{KeyPair, KeyPairGenerator, PrivateKey}

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import util.Messages
import util.Messages._

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  var testNr = 0
  "A transaction" must {
    "succeed with 1 coordinator and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 4 coordinators and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 1 coordinator and 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").withOccurrences(4).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "abort with 1 coordinator and 1 participant (participant should never be asked to prepare)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").expect {
        cs.foreach(c => c ! Messages.InitAbort(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "abort with 4 coordinators and 1 participant (participant should never be asked to prepare)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").expect {
        cs.foreach(c => c ! Messages.InitAbort(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "abort with 1 coordinator and 4 participants (participant should never be asked to prepare)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        cs.foreach(c => c ! Messages.InitAbort(t.id, p).fakesign())
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
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to unilaterally abort a transaction (1 coordinator)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(5).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to unilaterally abort a transaction (4 coordinators)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 4, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(5).expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
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
      ps.foreach(p => p ! PropagateTransaction(t0).fakesign())
      val t1 = Transaction(1)
      ps.foreach(p => p ! PropagateTransaction(t1).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t0.id, p).fakesign())
      }
      LoggingTestKit.info("Committed transaction 1").expect {
        cs.foreach(c => c ! Messages.InitCommit(t1.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "the initiator" must {
    "be able to abort in-flight commit" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t0 = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t0).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      cs.foreach(c => c ! Messages.InitCommit(t0.id, p).fakesign())
      LoggingTestKit.info("Aborted transaction 0").expect {
        cs.foreach(c => c ! Messages.InitAbort(t0.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "3 normal coordinators and 1 failed coordinator" must {
    "succeed with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "3 normal coordinators and 1 byzantine non-primary coordinator" must {
    "succeed with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 0, 0, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "3 normal coordinators and 1 byzantine primary coordinator" must {
    "succeed with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 0, 1)
      val t = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t).fakesign())
      Thread.sleep(100) // make sure the transaction fully propagated
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        cs.foreach(c => c ! Messages.InitCommit(t.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "a coordinator" must {
    "be able to suggest a view change if the timeout is exceeded" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(0, 1, 0,0,0,0,1)
      val t0 = Transaction(0)
      ps.foreach(p => p ! PropagateTransaction(t0).fakesign())
      Thread.sleep(100)
      val p = ps(0)
      LoggingTestKit.info("View change not implemented.").expect {
        cs.foreach(c => c ! InitCommit(t0.id, p).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  def spawnAll(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0, nFailedCoordinators: Int = 0, nByzantinePrimaryCoord: Int = 0, nByzantineOtherCoord: Int = 0, nSlowCoord: Int = 0): (Array[Messages.Coordinator], Array[Messages.Participant]) = {
    val cs = new Array[Messages.Coordinator](nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord + nSlowCoord)
  //def spawnAll(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0): (Array[Messages.Coordinator], Array[Messages.Participant]) = {
    //val cs = new Array[Messages.Coordinator](nCoordinators)

    var kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    var masterKey = kpg.generateKeyPair

    for (x <- 0 until nByzantinePrimaryCoord) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true, slow = false), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord until nByzantinePrimaryCoord + nCoordinators) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = false, slow = false), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = false, byzantine = false, slow = false), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true, slow = false), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord + nSlowCoord) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true, slow = true), testNr + "Coordinator-" + x)
    }
    cs.foreach { x => x ! Messages.Setup(cs).fakesign() }
    val ps = new Array[Messages.Participant](nCommittingParticipants + nAbortingParticipants)
    for (x <- 0 until nCommittingParticipants) {
      ps(x) = spawn(Participant(cs, Decision.COMMIT, genSignedKey(kpg, masterKey), masterKey.getPublic()), testNr + "Participant-" + x)
    }
    for (x <- nCommittingParticipants until nCommittingParticipants + nAbortingParticipants) {
      ps(x) = spawn(Participant(cs, Decision.ABORT, genSignedKey(kpg, masterKey), masterKey.getPublic()), testNr + "Participant-" + x)
    }
    (cs, ps)
  }

  def genSignedKey(kpg: KeyPairGenerator, masterKey: KeyPair): (PrivateKey, SignedPublicKey) = {
    val keyPair = kpg.generateKeyPair
    val s: java.security.Signature = java.security.Signature.getInstance("SHA512withRSA");
    s.initSign(masterKey.getPrivate)
    s.update(BigInt(keyPair.getPublic.hashCode()).toByteArray)
    (keyPair.getPrivate, (keyPair.getPublic, s.sign()))
  }
}
