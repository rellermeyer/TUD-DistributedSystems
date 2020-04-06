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
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 4 coordinators and 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed with 1 coordinator and 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }
  "A participant" must {
    "be able to unilaterally abort a transaction" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to unilaterally abort a transaction (4 participants)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 3, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to unilaterally abort a transaction (4 coordinators)" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 3, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "The initiator" must {
    "be able to abort" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 4 coordinators" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 4)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
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
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t0, Decision.COMMIT, ps, p).fakesign()
      }
      LoggingTestKit.info("Committed transaction 1").expect {
        p ! AppointInitiator(t1, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "3 normal coordinators and 1 non-responsive coordinator" must {
    "succeed commit with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed abort with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "3 normal coordinators and 1 byzantine non-primary coordinator" must {
    "succeed commit with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }

    "succeed abort with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
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
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed abort with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 1, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "a coordinator" must {
    "be able to suggest a view change if the timeout is exceeded" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(0, 1, 0, 0, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.error("View change requested but not implemented yet.").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
  }

  "Latency & throughput test" must {
    "2 participants" in {
      testNr = testNr + 1
      latencyThroughputTest(2)
    }
    "4 participants" in {
      testNr = testNr + 1
      latencyThroughputTest(4)
    }
    "6 participants" in {
      testNr = testNr + 1
      latencyThroughputTest(6)
    }
    "8 participants" in {
      testNr = testNr + 1
      latencyThroughputTest(8)
    }
    "10 participants" in {
      testNr = testNr + 1
      latencyThroughputTest(10)
    }
  }

  def latencyThroughputTest(nParticipants: Int) = {
    val (cs, ps) = spawnAll(4, nParticipants)
    val numberOfTransactions = 100
    var totalLatency: Long = 0
    val timerStart = System.currentTimeMillis()
    for (id <- 0 until numberOfTransactions) {
      val latencyTimerStart: Long = System.currentTimeMillis()
      val t = Transaction(id)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction " + id).withOccurrences(nParticipants).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      totalLatency += System.currentTimeMillis() - latencyTimerStart
    }
    val delay = System.currentTimeMillis() - timerStart
    cs.foreach(x => testKit.stop(x))
    ps.foreach(x => testKit.stop(x))
    println("Average latency (ms): " + (totalLatency / numberOfTransactions))
    println("Throughput (transactions/s): " + numberOfTransactions / (delay.toFloat / 1000))
  }

  def spawnAll(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0, nFailedCoordinators: Int = 0, nByzantinePrimaryCoord: Int = 0, nByzantineOtherCoord: Int = 0, nSlowCoord: Int = 0): (Array[Messages.CoordinatorRef], Array[Messages.ParticipantRef]) = {
    val cs = new Array[Messages.CoordinatorRef](nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord + nSlowCoord)
    //def spawnAll(nCoordinators: Int, nCommittingParticipants: Int, nAbortingParticipants: Int = 0): (Array[Messages.Coordinator], Array[Messages.Participant]) = {
    //val cs = new Array[Messages.Coordinator](nCoordinators)

    val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    val masterKey = kpg.generateKeyPair
    val setupKeyPair = genSignedKey(kpg, masterKey)

    for (x <- 0 until nByzantinePrimaryCoord) {
      cs(x) = spawn(Coordinator(new Coordinator(_, genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true, slow = false)), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord until nByzantinePrimaryCoord + nCoordinators) {
      cs(x) = spawn(Coordinator(new Coordinator(_, genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = false, slow = false)), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators) {
      cs(x) = spawn(Coordinator(new Coordinator(_, genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = false, byzantine = false, slow = false)), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord) {
      cs(x) = spawn(Coordinator(new Coordinator(_, genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = true, slow = false)), testNr + "Coordinator-" + x)
    }
    for (x <- nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord until nByzantinePrimaryCoord + nCoordinators + nFailedCoordinators + nByzantineOtherCoord + nSlowCoord) {
      cs(x) = spawn(Coordinator(new Coordinator(_, genSignedKey(kpg, masterKey), masterKey.getPublic(), operational = true, byzantine = false, slow = true)), testNr + "Coordinator-" + x)
    }
    cs.foreach { x => x ! Messages.Setup(cs).sign(setupKeyPair) }
    val ps = new Array[Messages.ParticipantRef](nCommittingParticipants + nAbortingParticipants)
    for (x <- 0 until nCommittingParticipants) {
      ps(x) = spawn(Participant(new FixedDecisionParticipant(_, cs, Decision.COMMIT, genSignedKey(kpg, masterKey), masterKey.getPublic())), testNr + "Participant-" + x)
    }
    for (x <- nCommittingParticipants until nCommittingParticipants + nAbortingParticipants) {
      ps(x) = spawn(Participant(new FixedDecisionParticipant(_, cs, Decision.ABORT, genSignedKey(kpg, masterKey), masterKey.getPublic())), testNr + "Participant-" + x)
    }
    (cs, ps)
  }

  def genSignedKey(kpg: KeyPairGenerator, masterKey: KeyPair): (PrivateKey, SignedPublicKey) = {
    val keyPair = kpg.generateKeyPair
    val s: java.security.Signature = java.security.Signature.getInstance("SHA512withRSA")
    s.initSign(masterKey.getPrivate)
    s.update(BigInt(keyPair.getPublic.hashCode()).toByteArray)
    (keyPair.getPrivate, (keyPair.getPublic, s.sign()))
  }
}
