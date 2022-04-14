package com.akkamidd

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import com.akkamidd.timestamp.MasterSiteTimestamp
import com.akkamidd.timestamp.MasterSiteTimestamp.MasterTimestampProtocol
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.file.{Files, Paths}
import java.io.{File, PrintWriter}
import scala.util.Random

class ExperimentTimestamps extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  "ExperimentTimestamp" must {
    "Execute random operations and capture inconsistencies together with execution times" in {
          def randomString(length: Int) = {
            val r = new scala.util.Random
            val sb = new StringBuilder
            for (_ <- 1 to length) {
              sb.append(r.nextPrintableChar)
            }
            sb.toString
          }

          Files.createDirectories(Paths.get("experiments/results"))

          val numRuns = 20
          val numSites = 20

          val spawningActorsTimeout = 100
          val timeoutSplit = 100
          val timeoutMerge = 100

          val randomNumberSites: Random.type = scala.util.Random
          randomNumberSites.setSeed(42)
          for (siteIdx <- 2 to numSites) {
            val randomNumberExperiment: Random.type = scala.util.Random
            randomNumberExperiment.setSeed(randomNumberSites.nextInt())
            for (runIdx <- 1 to numRuns) {
              val random: Random.type = scala.util.Random
              random.setSeed(randomNumberExperiment.nextInt())

              val experimentStartMillis = System.currentTimeMillis

              val masterSite: ActorSystem[MasterTimestampProtocol] = ActorSystem(MasterSiteTimestamp(debugMode = false), "MasterSiteTimestamp")

              val listSiteNames = List.range(0, siteIdx).map("Site" + _.toString)
              var listFilenames = List[String]()

              var partitionList: List[Set[String]] = UtilFuncsTimestamp.spawnSites(masterSystem = masterSite, siteNameList = listSiteNames, timeout = spawningActorsTimeout)

              var thresholdSplit = 20
              var thresholdMerge = 20

              val execFileName = "experiments/results/run_" + runIdx + "_timestamps_sites_" + siteIdx + "_exec.txt"
              val icdFileName = "experiments/results/run_" + runIdx + "_timestamps_sites_" + siteIdx + "_icd.txt"
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
                    val randomSite = listSiteNames(random.nextInt(listSiteNames.size))
                    val time = System.currentTimeMillis().toString
                    val fileName = randomString(5) + ".txt"
                    listFilenames = listFilenames :+ fileName
                    UtilFuncsTimestamp.callUploadFile(randomSite, time, masterSite, fileName, partitionList)

                  // Update
                  case x if x > 10 && x <= 50 =>
                    if (listFilenames.nonEmpty) {
                      val randomSite = listSiteNames(random.nextInt(listSiteNames.size))
                      val randomFileIndex = random.nextInt(listFilenames.size)
                      val fileName = listFilenames(randomFileIndex)
                      UtilFuncsTimestamp.callUpdateFile(randomSite, fileName, masterSite, partitionList)
                    }

                  // Split
                  case x if x > 50 && x <= 75 =>
                    if (thresholdSplit != 0) {
                      val randomSite = listSiteNames(random.nextInt(listSiteNames.size))
                      val previousPartitionList = partitionList
                      partitionList = UtilFuncsTimestamp.callSplit(masterSite, partitionList, randomSite, timeoutSplit, timeoutSplit)
                      if (!previousPartitionList.equals(partitionList)) {
                        thresholdSplit = thresholdSplit - 1
                      }
                    }

                  // Merge
                  case x if x > 75 && x < 100 =>
                    if (thresholdMerge != 0) {
                      val randomSite1 = listSiteNames(random.nextInt(listSiteNames.size))
                      val randomSite2 = listSiteNames(random.nextInt(listSiteNames.size))
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
              masterSite.log.info("Experiment Timestamp (number of sites: " + siteIdx + ", run: " + runIdx + ") ended - time: " + estimatedTime.toString)

              writerExec.write(estimatedTime.toString)
              writerExec.close()
              writerIcd.get.close()
            }
          }
        }
      }
}