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
      "be able to unilaterally abort a transaction (4 participants)" in {
        testNr = testNr + 1
        val (cs, ps) = spawnAll(1, 3, 1)
        val t = Transaction(0)
        LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
          ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
        }
        cs.foreach(x => testKit.stop(x))
        ps.foreach(x => testKit.stop(x))
      }
      "be able to unilaterally abort a transaction (4 coordinators)" in {
        testNr = testNr + 1
        val (cs, ps) = spawnAll(4, 3, 1)
        val t = Transaction(0)
        LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
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

  "The initiator" must {
    "be able to abort with 4 coordinators" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(4, 1, 1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(2).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(1)).fakesign()) //aborting participant is initiator and should initAbort
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "be able to abort with 4 participants" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(1, 3, 1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(3)).fakesign()) //aborting participant  initiator and should initAbort
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
      LoggingTestKit.info("Committed transaction 0").expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }
    "succeed abort with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 0, 1, 1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
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
      LoggingTestKit.info("Committed transaction 0").expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
      }
      cs.foreach(x => testKit.stop(x))
      ps.foreach(x => testKit.stop(x))
    }

    "succeed abort with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 0, 1,0,0,1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
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
    "succeed abort with 1 participant" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(3, 0, 1,0,1)
      val t = Transaction(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
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
    }/* //tests below still fail
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
    }*/
  }

  def latencyThroughputTest(nParticipants: Int) = {
    val (cs, ps) = spawnAll(4, nParticipants)
    val numberOfTransactions = 100
    var totalLatency: Long = 0
    val timerStart = System.currentTimeMillis()
    for (id <- 0 until numberOfTransactions) {
      val latencyTimerStart: Long = System.currentTimeMillis()
      val t = Transaction(id)
      LoggingTestKit.info("Committed transaction " + id).withOccurrences(nParticipants).expect {
        ps.foreach(p => p ! PropagateTransaction(t, ps(0)).fakesign())
      }
      totalLatency += System.currentTimeMillis() - latencyTimerStart
    }
    val delay = System.currentTimeMillis() - timerStart
    cs.foreach(x => testKit.stop(x))
    ps.foreach(x => testKit.stop(x))
    println("Average latency (ms): " + (totalLatency/numberOfTransactions))
    println("Throughput (transactions/s): " + numberOfTransactions/(delay.toFloat/1000))
  }

  "a coordinator" must {
    "be able to suggest a view change if the timeout is exceeded" in {
      testNr = testNr + 1
      val (cs, ps) = spawnAll(0, 1, 0, 0, 0, 0, 1)
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

    val kpg: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    kpg.initialize(2048)
    val masterKey = kpg.generateKeyPair

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
