package com.akkamidd.deprecated_experiments

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.UtilFuncsTimestamp
import com.akkamidd.timestamp.MasterSiteTimestamp
import com.akkamidd.timestamp.MasterSiteTimestamp.MasterTimestampProtocol
import org.scalatest.wordspec.AnyWordSpecLike

import java.io.{File, PrintWriter}
import scala.util.Random

class Experiment2Timestamp extends ScalaTestWithActorTestKit with AnyWordSpecLike  {
  "Experiment 2 Timestamp" must {
    "Detect Inconsistency" in {
      def randomString(length: Int) = {
        val r = new scala.util.Random
        val sb = new StringBuilder
        for (_ <- 1 to length) {
          sb.append(r.nextPrintableChar)
        }
        sb.toString
      }

      val numRuns = 3
      val numSites = 10

      val spawningActorsTimeout = 200
      val timeoutSplit = 200
      val timeoutMerge = 200

      val randomNumberExperiment: Random.type = scala.util.Random
      randomNumberExperiment.setSeed(50)
      for (i <- 1 to numRuns) {
        val random: Random.type = scala.util.Random
        random.setSeed(randomNumberExperiment.nextInt())

        val experimentStartMillis = System.currentTimeMillis

        val masterSite: ActorSystem[MasterTimestampProtocol] = ActorSystem(MasterSiteTimestamp(debugMode = false), "MasterSiteTimestamp")

        val listSiteNames = List.range(0, numSites).map("Site" + _.toString)
        var listFilenames = List[String]()

        var partitionList: List[Set[String]] = UtilFuncsTimestamp.spawnSites(masterSystem = masterSite, siteNameList = listSiteNames, timeout = spawningActorsTimeout)

        var thresholdSplit = 5
        var thresholdMerge = 5

        val execFileName = "experiments/results/run" + i + "_experiment2timestamp_exec.txt"
        val icdFileName = "experiments/results/run" + i + "_experiment2timestamp_icd.txt"
        val execFile = new File(execFileName)
        val icdFile = new File(icdFileName)
        execFile.createNewFile()
        icdFile.createNewFile()
        val writerExec = new PrintWriter(new File(execFileName))
        val writerIcd = Option(new PrintWriter(new File(icdFileName)))

        // Can never have more merges than splits so only needs to check whether the merge threshold has been reached.
        while (thresholdMerge > 0) {
          val randomValue = random.nextInt(100) // 0 to 99

          randomValue match {
            // Upload
            case x if x <= 10 =>
              val randomSite = listSiteNames(random.nextInt(numSites))
              val time = System.currentTimeMillis().toString
              val fileName = randomString(5) + ".txt"
              listFilenames = listFilenames :+ fileName
              UtilFuncsTimestamp.callUploadFile(randomSite, time, masterSite, fileName, partitionList)

            // Update
            case x if x > 10 && x <= 50 =>
              if (listFilenames.nonEmpty) {
                val randomSite = listSiteNames(random.nextInt(numSites))
                val randomFileIndex = random.nextInt(listFilenames.size)
                val fileName = listFilenames(randomFileIndex)
                UtilFuncsTimestamp.callUpdateFile(randomSite, fileName, masterSite, partitionList)
              }

            // Split
            case x if x > 50 && x <= 75 =>
              if (thresholdSplit != 0) {
                val randomSite = listSiteNames(random.nextInt(numSites))
                val previousPartitionList = partitionList
                partitionList = UtilFuncsTimestamp.callSplit(masterSite, partitionList, randomSite, timeoutSplit, timeoutSplit)
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
                partitionList = UtilFuncsTimestamp.callMerge(randomSite1, randomSite2, masterSite, partitionList, timeoutMerge, timeoutMerge, writerIcd)
                if (!previousPartitionList.equals(partitionList)) {
                  thresholdMerge = thresholdMerge - 1
                }
              }
          }
        }

        UtilFuncsTimestamp.terminateSystem(masterSite)

        val estimatedTime = System.currentTimeMillis - experimentStartMillis
        masterSite.log.info("Experiment 2 Timestamp ended - time: " + estimatedTime.toString)

        writerExec.write(estimatedTime.toString)
        writerExec.close()
        writerIcd.get.close()
      }
    }
  }
}
