package actors

import akka.actor.testkit.typed.scaladsl.{LoggingTestKit, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import util.Messages._
import util._

class CoordinatorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  val spawner = new SpawnerScalaTestWithActorTestKitImpl(this)

  "A transaction" must {
    "succeed with 1 coordinator and 1 participant" in {
      val (cs, ps) = spawner(1, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "succeed with 4 coordinators and 1 participant" in {
      val (cs, ps) = spawner(4, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "succeed with 1 coordinator and 4 participants" in {
      val (cs, ps) = spawner(1, 4)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }
  "A participant" must {
    "be able to unilaterally abort a transaction" in {
      val (cs, ps) = spawner(1, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "be able to unilaterally abort a transaction (4 participants)" in {
      val (cs, ps) = spawner(1, 3, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "be able to unilaterally abort a transaction (4 coordinators)" in {
      val (cs, ps) = spawner(4, 3, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "The initiator" must {
    "be able to abort" in {
      val (cs, ps) = spawner(1, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "be able to abort with 4 coordinators" in {
      val (cs, ps) = spawner(4, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "be able to abort with 4 participants" in {
      val (cs, ps) = spawner(1, 4)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(4).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "2 transactions" must {
    "succeed" in {
      val (cs, ps) = spawner(1, 1)
      val t0 = Transaction(0)
      val t1 = Transaction(1)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t0, Decision.COMMIT, ps, p).fakesign()
      }
      LoggingTestKit.info("Committed transaction 1").expect {
        p ! AppointInitiator(t1, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "3 normal coordinators and 1 non-responsive coordinator" must {
    "succeed commit with 1 participant" in {
      val (cs, ps) = spawner(3, 1, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "succeed abort with 1 participant" in {
      val (cs, ps) = spawner(3, 1, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "3 normal coordinators and 1 byzantine non-primary coordinator" must {
    "succeed commit with 1 participant" in {
      val (cs, ps) = spawner(3, 1, 0, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }

    "succeed abort with 1 participant" in {
      val (cs, ps) = spawner(3, 1, 0, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "3 normal coordinators and 1 byzantine primary coordinator" must {
    "succeed with 1 participant" in {
      val (cs, ps) = spawner(3, 1, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Committed transaction 0").expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
    "succeed abort with 1 participant" in {
      val (cs, ps) = spawner(3, 1, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.info("Aborted transaction 0").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.ABORT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "a coordinator" must {
    "be able to suggest a view change if the timeout is exceeded" in {
      val (cs, ps) = spawner(0, 1, 0, 0, 0, 0, 1)
      val t = Transaction(0)
      val p = ps(0)
      LoggingTestKit.error("View change requested but not implemented yet.").withOccurrences(1).expect {
        p ! AppointInitiator(t, Decision.COMMIT, ps, p).fakesign()
      }
      (cs ++ ps).foreach(testKit.stop(_))
    }
  }

  "Latency & throughput test" must {
    "2 participants" in {
      latencyThroughputTest(2)
    }
    "4 participants" in {
      latencyThroughputTest(4)
    }
    "6 participants" in {
      latencyThroughputTest(6)
    }
    "8 participants" in {
      latencyThroughputTest(8)
    }
    "10 participants" in {
      latencyThroughputTest(10)
    }
  }

  def latencyThroughputTest(nParticipants: Int) = {
    val (cs, ps) = spawner(4, nParticipants)
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
    (cs ++ ps).foreach(testKit.stop(_))
    println("Average latency (ms): " + (totalLatency / numberOfTransactions))
    println("Throughput (transactions/s): " + numberOfTransactions / (delay.toFloat / 1000))
  }

}
