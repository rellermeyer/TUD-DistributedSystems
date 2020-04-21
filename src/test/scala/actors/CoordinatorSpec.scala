package actors

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import util.Messages._
import util._
import java.io._

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val spawner = new SpawnerScalaTestWithActorTestKitImpl(this)

  "A transaction" must {
    "succeed with 1 coordinator and 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nCommittingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "succeed with 4 coordinators and 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 4, nCommittingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "succeed with 1 coordinator and 4 participants" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nCommittingParticipants = 4)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }
  "A participant" must {
    "be able to unilaterally abort a transaction" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nAbortingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "be able to unilaterally abort a transaction (4 participants)" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nCommittingParticipants = 3, nAbortingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "be able to unilaterally abort a transaction (4 coordinators)" in {
      val (coordinators, participants) = spawner(nCoordinators = 4, nCommittingParticipants = 3, nAbortingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "The initiator" must {
    "be able to abort" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nCommittingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "be able to abort with 4 coordinators" in {
      val (coordinators, participants) = spawner(nCoordinators = 4, nCommittingParticipants = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "be able to abort with 4 participants" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nCommittingParticipants = 4)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.ABORT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "2 transactions" must {
    "succeed" in {
      val (coordinators, participants) = spawner(nCoordinators = 1, nCommittingParticipants = 1)
      val t0 = Transaction(0)
      val t1 = Transaction(1)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t0, Decision.COMMIT, participants).fakesign()
      }
      LoggingTestKit.info("Committed transaction 1").expect {
        p ! AppointInitiator(t1, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "3 normal coordinators and 1 non-responsive coordinator" must {
    "succeed commit with 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 3, nCommittingParticipants = 1, nFailedCoordinators = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "succeed abort with 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 3, nCommittingParticipants = 1, nFailedCoordinators = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "3 normal coordinators and 1 byzantine non-primary coordinator" must {
    "succeed commit with 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 3, nCommittingParticipants = 1, nByzantineOtherCoord = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }

    "succeed abort with 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 3, nCommittingParticipants = 1, nByzantineOtherCoord = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "3 normal coordinators and 1 byzantine primary coordinator" must {
    "succeed with 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 3, nCommittingParticipants = 1, nByzantinePrimaryCoord = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
    "succeed abort with 1 participant" in {
      val (coordinators, participants) = spawner(nCoordinators = 3, nCommittingParticipants = 1, nByzantinePrimaryCoord = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "a coordinator" must {
    "be able to suggest a view change if the timeout is exceeded" in {
      val (coordinators, participants) = spawner(nCommittingParticipants = 1, nSlowCoord = 1)
      val t = Transaction(0)
      val p = participants(0)
      LoggingTestKit.error("View change requested but not implemented yet.").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      (coordinators ++ participants).foreach(testKit.stop(_))
    }
  }

  "Latency & throughput test" must {
    val numberOfRuns = 10
    for (i <- 0 until numberOfRuns) {
      ("2 participants - run " + i) in {
        latencyThroughputTest(2,0)
      }
      ("4 participants - run " + i) in {
        latencyThroughputTest(4,0)
      }
      ("6 participants - run " + i) in {
        latencyThroughputTest(6,0)
      }
      ("8 participants - run " + i) in {
        latencyThroughputTest(8,0)
      }
      ("10 participants - run " + i) in {
        latencyThroughputTest(10,0)
      }
    }
  }
  "Byzantine coordinator latency & throughput test" must {
    val numberOfRuns = 10

    for (i <- 0 until numberOfRuns) {
      ("2 participants - run " + i) in {
        latencyThroughputTest(2,1)
      }
       ("4 participants - run " + i) in {
        latencyThroughputTest(4,1)
      }
      ("6 participants - run " + i) in {
        latencyThroughputTest(6,1)
      }
      ("8 participants - run " + i) in {
        latencyThroughputTest(8,1)
      }
      ("10 participants - run " + i) in {
        latencyThroughputTest(10,1)
      }
    }
  }

  def latencyThroughputTest(nParticipants: Int,nByzantineOtherCoord: Int): Unit = {
    val (coordinators, participants) = spawner(nCoordinators = 4-nByzantineOtherCoord, nCommittingParticipants = nParticipants,0,0,0,nByzantineOtherCoord)
    val numberOfTransactions = 100
    var totalLatency: Long = 0
    val timerStart = System.currentTimeMillis()
    for (id <- 0 until numberOfTransactions) {
      val latencyTimerStart: Long = System.currentTimeMillis()
      val t = Transaction(id)
      val p = participants(0)
      LoggingTestKit.info("Committed transaction " + id).withOccurrences(nParticipants).expect {
        p ! AppointInitiator(t, Decision.COMMIT, participants).fakesign()
      }
      totalLatency += System.currentTimeMillis() - latencyTimerStart
    }
    val delay = System.currentTimeMillis() - timerStart
    (coordinators ++ participants).foreach(testKit.stop(_))
    val latency = totalLatency / numberOfTransactions
    val throughput = numberOfTransactions / (delay.toFloat / 1000)
    println("Average latency (ms): " + latency)
    println("Throughput (transactions/s): " + throughput)
    val measurements = new File("measurements.csv")
    val bw = new BufferedWriter(new FileWriter(measurements,true))
    bw.write(nParticipants + "," + nByzantineOtherCoord + "," + latency + "," + throughput + "\n")
    bw.close()
  }

}
