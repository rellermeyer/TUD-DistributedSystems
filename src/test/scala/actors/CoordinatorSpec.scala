package actors

import java.security.{KeyPair, KeyPairGenerator, PrivateKey}

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import util.Messages
import util.Messages._

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  var testNr = 0
  // abortTimer used to delay the initAbort
  // too early and the registration is not complete
  // too late and the transaction is already committed
  //TODO: create 'abortingInitiator' that is participant 0 and sends initAbort some delay after its own initCommit
  val abortTimer = 40

  "A transaction" must {
    "succeed with 1 coordinator and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 4 coordinators and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 1 coordinator and 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      LoggingTestKit.info("Committed transaction 0").withOccurrences(4).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }
  // starting an abort without having started the respective commit did not make any practical sense
  // it also required extra work to implement for this new version so was removed instead
    "A participant" must {
      "be able to abort a transaction" in {
        testNr = testNr + 1
        val (cs, ps) = spawnAll(1, 0, 1)
        val t = Transaction(0)
        LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        }
        cs.foreach(x => testKit.stop(x))
        ps.foreach(x => testKit.stop(x))
      }
      "be able to unilaterally abort a transaction (1 coordinator)" in {
        testNr = testNr + 1
        val (cs, ps) = spawnAll(1, 4, 1)
        val t = Transaction(0)
        LoggingTestKit.info("Aborted transaction 0").withOccurrences(5).expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        }
        cs.foreach(x => testKit.stop(x))
        ps.foreach(x => testKit.stop(x))
      }
      "be able to unilaterally abort a transaction (4 coordinators)" in {
        testNr = testNr + 1
        val (cs, ps) = spawnAll(4, 4, 1)
        val t = Transaction(0)
        LoggingTestKit.info("Aborted transaction 0").withOccurrences(5).expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
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
        val t1 = Transaction(1)
        LoggingTestKit.info("Committed transaction 0").expect {
          ps.foreach(p => p ! PropagateTransaction(t0, ps(0)).fakesign())
        }
        LoggingTestKit.info("Committed transaction 1").expect {
          ps.foreach(p => p ! PropagateTransaction(t1, ps(0)).fakesign())
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
        LoggingTestKit.info("Committed transaction 0").expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
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
        LoggingTestKit.info("Committed transaction 0").expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
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
        LoggingTestKit.info("Committed transaction 0").expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        }
        cs.foreach(x => testKit.stop(x))
        ps.foreach(x => testKit.stop(x))
      }
    }
  "The initiator" must {
    "be able to abort an in-flight transaction" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1, 0)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        Thread.sleep(abortTimer)
        cs.foreach(c => c ! InitAbort(t.id, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 4 coordinators" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1, 0)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        Thread.sleep(abortTimer)
        cs.foreach(c => c ! InitAbort(t.id, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4, 0)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        Thread.sleep(abortTimer)
        cs.foreach(c => c ! InitAbort(t.id, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }

    "be able to abort with 3 coordinators + 1 nonresponsive" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        Thread.sleep(abortTimer)
        cs.foreach(c => c ! InitAbort(t.id, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 3 coordinators + 1 byzantine nonprimary" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0,0,0,1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        Thread.sleep(abortTimer)
        cs.foreach(c => c ! InitAbort(t.id, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 3 coordinators + 1 byzantine primary" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0,0,1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        Thread.sleep(abortTimer)
        cs.foreach(c => c ! InitAbort(t.id, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  def spawnAll(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0, nFailedCoordinators: Int = 0, nByzantinePrimaryCoord: Int = 0, nByzantineOtherCoord: Int = 0): (Array[Messages.Coordinator], Array[Messages.Participant]) = {
    val cs = new Array[Messages.Coordinator](nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord)

    var kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    var masterKey = kpg.generateKeyPair

    for (x <- 0 until nByzantinePrimaryCoord) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord until nByzantinePrimaryCoord + nCoordinators) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = false), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = false, byzantine = false), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord) {
      cs(x) = spawn(Coordinator(genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true), testNr + "Coordinator-" + x)
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
