package com.akkamidd.deprecated_experiments

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.UtilFuncs
import com.akkamidd.actors.MasterSite
import com.akkamidd.actors.MasterSite.MasterSiteProtocol
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, PrintWriter}
import scala.util.Random

class Experiment3 extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "Experiment 3" must {
    "Detect Inconsistency" in {
      def randomString(length: Int) = {
        val r = new scala.util.Random
        val sb = new StringBuilder
        for (_ <- 1 to length) {
          sb.append(r.nextPrintableChar)
        }
        sb.toString
      }

      val numRuns = 10
      val numSites = 15

      val spawningActorsTimeout = 200
      val timeoutSplit = 200
      val timeoutMerge = 200

      val randomNumberExperiment: Random.type = scala.util.Random
      randomNumberExperiment.setSeed(50)
      for (i <- 1 to numRuns) {
        val random: Random.type = scala.util.Random
        random.setSeed(randomNumberExperiment.nextInt())

        val experimentStartMillis = System.currentTimeMillis

        val masterSite: ActorSystem[MasterSiteProtocol] = ActorSystem(MasterSite(debugMode = false), "MasterSite")

        val listSiteNames = List.range(0, numSites).map("Site" + _.toString)
        var listOriginPointers = Map[String, String]()

        var partitionList: List[Set[String]] = UtilFuncs.spawnSites(masterSystem = masterSite, siteNameList = listSiteNames, timeout = spawningActorsTimeout)

        var thresholdSplit = 6
        var thresholdMerge = 4

        val execFileName = "experiments/results/run" + i + "_experiment3_exec.txt"
        val icdFileName = "experiments/results/run" + i + "_experiment3_icd.txt"
        val execFile = new File(execFileName)
        val icdFile = new File(icdFileName)
        execFile.createNewFile()
        icdFile.createNewFile()
        val writerExec = new PrintWriter(new File(execFileName))
        val writerIcd = new PrintWriter(new File(icdFileName))

        // Can never have more merges than splits so only needs to check whether the merge threshold has been reached.
        while (thresholdMerge > 0) {
          val randomValue = random.nextInt(100) // 0 to 99

          randomValue match {
            // Upload
            case x if x <= 10 =>
              val randomSite = listSiteNames(random.nextInt(numSites))
              val time = System.currentTimeMillis().toString
              listOriginPointers = listOriginPointers + (randomSite -> time)
              val fileName = randomString(5) + ".txt"
              UtilFuncs.callUploadFile(randomSite, time, masterSite, fileName, partitionList)

            // Update
            case x if x > 10 && x <= 50 =>
              if (listOriginPointers.nonEmpty) {
                val randomSite = listSiteNames(random.nextInt(numSites))
                val randomFileIndex = random.nextInt(listOriginPointers.size)
                val tuple = listOriginPointers.toList(randomFileIndex)
                UtilFuncs.callUpdateFile(randomSite, tuple, masterSite, partitionList)
              }

            // Split
            case x if x > 50 && x <= 75 =>
              if (thresholdSplit != 0) {
                val randomSite = listSiteNames(random.nextInt(numSites))
                val previousPartitionList = partitionList
                partitionList = UtilFuncs.callSplit(masterSite, partitionList, randomSite, timeoutSplit, timeoutSplit)
                if (!previousPartitionList.equals(partitionList)) {
                  thresholdSplit = thresholdSplit - 1
                }
              }

            // Merge
            case x if x > 75 && x < 100 =>
              if (thresholdMerge != 0) {
                val randomSite1 = listSiteNames(random.nextInt(numSites))
                val randomSite2 = listSiteNames(random.nextInt(numSites))
                val previousPartitionList = partitionList
                partitionList = UtilFuncs.callMerge(randomSite1, randomSite2, masterSite, partitionList, timeoutMerge, timeoutMerge, Option(writerIcd))
                if (!previousPartitionList.equals(partitionList)) {
                  thresholdMerge = thresholdMerge - 1
                }
              }
          }
        }

        UtilFuncs.terminateSystem(masterSite)

        val estimatedTime = System.currentTimeMillis - experimentStartMillis
        masterSite.log.info("Experiment 3 ended - time: " + estimatedTime.toString)

        writerExec.write(estimatedTime.toString)
        writerExec.close()
        writerIcd.close()
      }
    }
  }
}