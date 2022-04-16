package com.akkamidd
import java.io.{File, PrintWriter}
import java.nio.file.{Files, Paths}
import scala.io.Source

object ResultsFormat {

  def intCount(filename: String): Int = {
    val count = Source.fromFile(filename).getLines.map {_.toInt}.sum
    count
  }

  def printVersionVector(numberOfSites: Int, numberOfRuns: Int): Unit = {
    val writer = new PrintWriter(new File("experiments/csv_format/version_vector_formatted.csv"))
    writer.write("sites,run,icd,exec\n")
    for (siteIdx <- 2 to numberOfSites) {
      for (runIdx <- 1 to numberOfRuns) {
        val icdCount = intCount("experiments/results/run_" + runIdx + "_version_vector_sites_" + siteIdx + "_icd.txt")
        val execCount = intCount("experiments/results/run_" + runIdx + "_version_vector_sites_" + siteIdx + "_exec.txt")
        writer.write(s"$siteIdx,$runIdx,$icdCount,$execCount\n")
      }
    }
    writer.close()
    print("Results have been formatted for Version Vector, see file: version_vector_formatted.csv\n")
  }

  def printTimestamp(numberOfSites: Int, numberOfRuns: Int): Unit = {
    val writer = new PrintWriter(new File("experiments/csv_format/timestamp_formatted.csv"))
    writer.write("sites,run,icd,exec\n")
    for (siteIdx <- 2 to numberOfSites) {
      for (runIdx <- 1 to numberOfRuns) {
        val icdCount = intCount("experiments/results/run_" + runIdx + "_timestamps_sites_" + siteIdx + "_icd.txt")
        val execCount = intCount("experiments/results/run_" + runIdx + "_timestamps_sites_" + siteIdx + "_exec.txt")
        writer.write(s"$siteIdx,$runIdx,$icdCount,$execCount\n")
      }
    }
    writer.close()
    print("Results have been formatted for Timestamps, see file: timestamp_formatted.csv")
  }

  def main(args: Array[String]): Unit = {
    Files.createDirectories(Paths.get("experiments/csv_format"))
    val numberOfSites = 20
    val numberOfRuns = 20

    printVersionVector(numberOfSites, numberOfRuns)
    printTimestamp(numberOfSites, numberOfRuns)
  }
}
