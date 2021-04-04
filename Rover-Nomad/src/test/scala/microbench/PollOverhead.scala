package microbench

import java.io.{BufferedWriter, FileWriter}

import au.com.bytecode.opencsv.CSVWriter
import utilities.Utilities
import votingapp.{Poll, PollChoice}
import scala.collection.JavaConversions._


import scala.collection.mutable.ListBuffer

class PollOverheadMicroBench(val numRepetitions: Int,
                             val numChoices: Int,
                             var roverCastDurations: List[Long] = List[Long](),
                             var nonRoverCastDurations: List[Long] = List[Long](),
                             var roverResultDurations: List[Long] = List[Long](),
                             var nonRoverResultDurations: List[Long] = List[Long](),
                             var roverSizes: List[Long] = List[Long](),
                             var nonRoverSizes: List[Long] = List[Long]()) {

    val roverPoll = Poll("microBenchPoll", numChoices)

    def initNonRover(numChoices: Int): Map[PollChoice, Int] = {
        var choices = List[PollChoice]()
        Range.inclusive(1, numChoices).foreach(choiceId => {
            choices = choices :+ PollChoice(s"Choice$choiceId")
        })
        val map = choices.map(choice => (choice,0)).toMap.withDefaultValue(0)
        return map
    }

    def benchmarkRoverPollCast(choiceIds: List[Int]): Unit = {

        for (choiceId <- choiceIds) {
            var benchInit = System.nanoTime()
            roverPoll.cast(roverPoll.choices(choiceId))
            roverCastDurations = roverCastDurations :+ (System.nanoTime() - benchInit)
        }
    }

    def benchmarkNonRoverPollCast(choiceIds: List[Int]): Unit = {
        var map = initNonRover(numChoices)

        for (choiceId <- choiceIds) {
            var benchInit = System.nanoTime()
            map = map updated (PollChoice(s"Choice${choiceId+1}"), map(PollChoice(s"Choice${choiceId+1}")) + 1)
            nonRoverCastDurations = nonRoverCastDurations :+ (System.nanoTime() - benchInit)
        }
    }

    def benchmarkRoverPollResult(polls: List[Poll]): Unit  = {
        for (poll <- polls) {
            var benchInit = System.nanoTime()
            val winner = poll.result.winner
            roverResultDurations = roverResultDurations :+ (System.nanoTime() - benchInit)
        }
    }

    def benchmarkNonRoverPollResult(polls: List[Poll]): Unit = {
        for (poll <- polls) {
            val map = poll.state.immutableState.map
            var benchInit = System.nanoTime()
            val winner = map.maxBy(_._2)._1
            nonRoverResultDurations = nonRoverResultDurations :+ (System.nanoTime() - benchInit)
        }
    }

    def getSizeOverhead(choiceIds: List[Int]): Unit = {

        for (choiceId <- choiceIds) {
            val roverPoll = Poll(null, numChoices)
            var map = initNonRover(numChoices)

            roverPoll.cast(roverPoll.choices(choiceId))
            map = map updated (PollChoice(s"Choice${choiceId+1}"), map(PollChoice(s"Choice${choiceId+1}")) + 1)

            roverSizes = roverSizes :+ Utilities.sizeOf(roverPoll.state)
            nonRoverSizes = nonRoverSizes :+ Utilities.sizeOf(map)
        }

        println(s"Mean rover size: ${Utilities.getMean(roverSizes)}, with std: ${Utilities.getStd(roverSizes)}")
        println(s"Mean non-rover size: ${Utilities.getMean(nonRoverSizes)}, with std: ${Utilities.getStd(nonRoverSizes)}")

        println(s"Size-overhead: ${Utilities.getOverhead(Utilities.getMean(nonRoverSizes), Utilities.getMean(roverSizes))}")
    }

    def runCast(randomChoices: List[Int]): Unit = {

        benchmarkRoverPollCast(randomChoices)
        benchmarkNonRoverPollCast(randomChoices)

        val meanRoverDurations =  Utilities.getMean(roverCastDurations.slice(2, numRepetitions))
        val meanNonRoverDurations = Utilities.getMean(nonRoverCastDurations.slice(2, numRepetitions))

        println(s"RoverPollCast: mean time in nano: $meanRoverDurations, std: ${Utilities.getStd(roverCastDurations.slice(2, numRepetitions))}")
        println(s"\t Ops/s: ${Utilities.oneSecInNano/meanRoverDurations}")

        println(s"NonRoverPollCast: mean time in nano $meanNonRoverDurations, std: ${Utilities.getStd(nonRoverCastDurations.slice(2, numRepetitions))}")
        println(s"\t Ops/s: ${Utilities.oneSecInNano/meanNonRoverDurations}")


        println("\n")
        println(f"Overhead: ${Utilities.getOverhead(meanNonRoverDurations.asInstanceOf[Double], meanRoverDurations.asInstanceOf[Double])}%1.4f")
    }

    def runResult(): Unit = {
        val randomPolls = Poll.generateRandomPolls("microBenchPollResult", numChoices, numRepetitions)

        benchmarkRoverPollResult(randomPolls)
        benchmarkNonRoverPollResult(randomPolls)

        val meanRoverDurations =  Utilities.getMean(roverResultDurations.slice(2, numRepetitions))
        val meanNonRoverDurations = Utilities.getMean(nonRoverResultDurations.slice(2, numRepetitions))
        println(s"RoverPollResult: mean time in nano: $meanRoverDurations, std: ${Utilities.getStd(roverResultDurations.slice(2, numRepetitions))}")
        println(s"\t Ops/s: ${Utilities.oneSecInNano/meanRoverDurations}")

        println(s"NonRoverPollResult: mean time in nano $meanNonRoverDurations, std: ${Utilities.getStd(nonRoverResultDurations.slice(2, numRepetitions))}")
        println(s"\t Ops/s: ${Utilities.oneSecInNano/meanNonRoverDurations}")


        println("\n")
        println(f"Overhead: ${Utilities.getOverhead(meanNonRoverDurations.asInstanceOf[Double], meanRoverDurations.asInstanceOf[Double])}%1.4f")

    }

    def toCSV(): Unit = {

        val outputFile = new BufferedWriter(new FileWriter(s"./results_poll_${java.time.Instant.now.getEpochSecond}.csv"))
        val csvWriter = new CSVWriter(outputFile)
        val csvHeader = Array("id", "nonRoverCastDurations", "roverCastDurations",
            "nonRoverResultDurations", "roverResultDurations",
            "nonRoverSizes", "roverSizes")
        var listOfRecords = new ListBuffer[Array[String]]()
        listOfRecords += csvHeader
        Range.inclusive(1, roverCastDurations.length).foreach(i => {
            listOfRecords += Array(i.toString, nonRoverCastDurations(i-1).toString, roverCastDurations(i-1).toString,
                nonRoverResultDurations(i-1).toString, roverResultDurations(i-1).toString,
                nonRoverSizes(i-1).toString, roverSizes(i-1).toString)
        })
        csvWriter.writeAll(listOfRecords.toList)
        outputFile.close()
    }

    def run(): Unit = {
        val randomChoices = Utilities.generateRandomInts(numRepetitions, numChoices)

        println("Benchmark vote casting")
        runCast(randomChoices)
        println(s"Rover cast benches: $roverCastDurations")
        println(s"Non Rover cast benches: $nonRoverCastDurations")

        println("\n")
        println("Benchmark poll result")
        runResult()
        println(s"Rover result benches: $roverResultDurations")
        println(s"Non Rover result benches: $nonRoverResultDurations")

        println("\n")
        println("Benchmark size overhead")
        getSizeOverhead(randomChoices)
        println(s"Rover sizes: $roverSizes")
        println(s"Non Rover sizes: $nonRoverSizes")

        println("\n")
        println("Generating CSV")
        toCSV()
    }
}


object PollOverheadMicroBench {
    def main(args: Array[String]): Unit = {
        val microBench = new PollOverheadMicroBench(5, 5)
        microBench.run()

    }
}
