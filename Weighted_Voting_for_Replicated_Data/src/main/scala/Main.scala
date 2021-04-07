import FileSystem.FileSystem
import VotingSystem.FileSuiteManager

import scala.util.Try

object Main {
  def main(args: Array[String]): Unit = {

    var fileSystem: FileSystem = null
    var manager: FileSuiteManager = null
    var numContainers = -1

    /**
     * UI flags
     */
    var exited = false
    var showedTutorial = false
    var createdFileSystem = false
    var createdSuite = false


    /**
     * Retreive Integer input from the user
     * @return user input
     */
    def getUserChoiceInt(): Int = {
      var validNum = false

      if (!showedTutorial) {
        println("(!) Your input: (Keep in mind that for all user inputs, values as specified are required!)")
        showedTutorial = true
      }

      while (!validNum) {
        val inputInt = scala.io.StdIn.readLine()
        if (Try(inputInt.toInt).isSuccess) {
          validNum = true
          return inputInt.toInt
        }
        else {
          println("Please enter an Integer value")
        }
      }
      return -1
    }

    /**
     * Retreive Double input from the user
     * @return user input
     */
    def getUserChoiceDouble(): Double = {
      var validNum = false

      while (!validNum) {
        val inputDouble = scala.io.StdIn.readLine()
        if (Try(inputDouble.toDouble).isSuccess) {
          validNum = true
          return inputDouble.toDouble
        }
        else {
          println("Please enter a Double value")
        }
      }
      0.0
    }

    /**
     * Provide interactive UI for creating containers, instantiate FileSystem and FileSuiteManager
     */
    def createFileSystemUI(): Unit = {
      var exitSetup = false
      var latencies = Seq.empty[Int]
      var blockingProbs = Seq.empty[Double]

      while (!exitSetup) {
        println("(!) Creating file system menu - Choose an event:")
        println("(-)  1: Add a container")
        println("(-)  0: Stop adding containers")
        val menu_integer = getUserChoiceInt()

        if (menu_integer == 0) {
          if (latencies.isEmpty) {
            println("(!) No containers were created, so no file system could be created. Returning to main menu\n")
          }
          else {
            fileSystem = FileSystem(latencies.length, latencies, blockingProbs)
            manager = FileSuiteManager(fileSystem)
            numContainers = latencies.length

            for (i <- latencies.indices) {
              println("(" + (i+1) + ") Created a container with latency: " + latencies(i) +
                      ", and blocking probability: " + blockingProbs(i))
            }
            createdFileSystem = true
          }
          exitSetup = true
          println("(!) Left menu for creating a file system\n")
        }
        else if (menu_integer == 1) {
          println("(!) Enter latency of the new container:")
          latencies = latencies :+ getUserChoiceInt()
          println("(!) Enter blocking probability of the new container:")
          blockingProbs = blockingProbs :+ getUserChoiceDouble()
        }
        else {
          println("(E) Please enter a valid number!\n")
        }
      }
    }

    /**
     * Provide interactive UI for instantiating new file suites
     */
    def createSuiteUI() : Unit = {
      println("(!) What is the ID of the new file?")
      val suiteID = getUserChoiceInt()
      println("(!) What is the r value of the file suite?")
      val suiteR = getUserChoiceInt()
      println("(!) What is the w value of the file suite?")
      val suiteW = getUserChoiceInt()
      var repWeights = Seq.empty[Int]

      for (i <- 0 until numContainers) {
        println("(!) What is the weight of the representative in container " + i + "?")
        val newWeight = getUserChoiceInt()
        repWeights = repWeights :+ newWeight
      }
      val result = manager.create(suiteID, suiteR, suiteW, repWeights)
      result match {
        case Left(f) => println("Could not create file suite:\n" + f.reason + "\n")
        case Right(r) => {
          createdSuite = true
          println("Successfully created file suite with id " + suiteID + "\n")
        }
      }
    }

    /**
     * Provide interactive UI for deleting file suites
     */
    def deleteSuiteUI() : Unit = {
      println("(!) What is the ID of the file you want to delete?")
      val suiteID = getUserChoiceInt()

      val result = manager.delete(suiteID)
      result match {
        case Left(f) => println("Could not delete file:\n" + f.reason + "\n")
        case Right(r) => {
          createdSuite = true
          println("Successfully deleted file with id " + suiteID + "\n")
        }
      }
    }

    /**
     * Provide interactive UI for reading from file suites
     */
    def readSuiteUI() : Unit = {
      println("(!) What is the ID of the file you want to read?")
      val suiteID = getUserChoiceInt()
      val result = manager.read(suiteID)
      result match {
        case Left(f) => println("Could not read file suite:\n" + f.reason + "\n")
        case Right(r) => println("Content of file " + suiteID + " is " + r._1 + ". Latency: " + r._2 + "\n")
      }
    }

    /**
     * Provide interactive UI for writing to file suites
     */
    def writeSuiteUI(): Unit = {
      println("(!) What is the ID of the file you want to write to?")
      val suiteID = getUserChoiceInt()
      println("(!) What is the integer content you want to write to file " + suiteID + "?")
      val newContent = getUserChoiceInt()
      val result = manager.write(suiteID, newContent)
      result match {
        case Left(f) => println("Could not write to file suite:\n" + f.reason + "\n")
        case Right(r) => println("Successfully written " + newContent +
                                " to file with id " + suiteID + ". Latency: " + r + "\n")
      }
    }

    /**
     * Provide interactive UI for user control of transactions
     */
    def startTransactionUI(): Unit = {
      var endedTransaction: Boolean = false
      manager.begin()

      while(!endedTransaction) {
        println("(!) Transaction menu - Choose an event:")
        println("(-)  1: Read from file")
        println("(-)  2: Write to file")
        println("(-)  3: Commit transaction")
        println("(-)  4: Abort transaction")

        val userChoice = getUserChoiceInt()

        if (userChoice == 1) {
          readSuiteUI()
        }
        else if (userChoice == 2) {
          writeSuiteUI()
        }
        else if (userChoice == 3) {
          manager.commit()
          endedTransaction = true
        }
        else if (userChoice == 4) {
          manager.abort()
          endedTransaction = true
        }
        else {
          println("(E) Please enter a valid number!\n")
        }
      }
    }

    /**
     * Provide interactive main menu UI
     */
    while (!exited) {
      println("(!) Main menu - Choose an event:")
      println("(-)  1: Create new file system")
      if (createdFileSystem) {
        println("(-)  2: Create new file")
      }
      if (createdFileSystem && createdSuite) {
        println("(-)  3: Delete file")
        println("(-)  4: Start new transaction")
      }
      println("(-)  0: Quit")

      val userChoice = getUserChoiceInt()

      if (userChoice == 0) {
        exited = true
        println("(!) User left UI")
      }
      else if (userChoice == 1) {
        createFileSystemUI()
      }
      else if (userChoice == 2 && createdFileSystem) {
        createSuiteUI()
      }
      else if (userChoice == 3 && createdFileSystem && createdSuite) {
        deleteSuiteUI()
      }
      else if (userChoice == 4 && createdFileSystem && createdSuite) {
        startTransactionUI()
      }
      else {
        println("(E) Please enter a valid number!\n")
      }
    }
  }
}
