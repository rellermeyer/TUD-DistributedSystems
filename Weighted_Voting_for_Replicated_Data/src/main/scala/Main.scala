import FileSystem.FileSystem
import VotingSystem.FileSuiteManager

object Main {
  def main(args: Array[String]): Unit = {

    var fileSystem: FileSystem = null
    var manager: FileSuiteManager = null
    var numContainers = -1

    //    UI (user interface):
    var exit_boolean = false
    var showedTutorial = false
    var createdFileSystem = false
    var createdSuite = false

    var menu_integer = -1
    var input_integer = -1

    // Retrieving input from the user
    def get_user_choice () : Int = {
      if (!showedTutorial) {
        println("(!) Your input: (Keep in mind that for all user inputs integers as specified are required!)")
        showedTutorial = true
      }
      input_integer = scala.io.StdIn.readInt()
      input_integer
    }


    def exitUI() : Unit = {
      println("(!) Leaving UI")
      exit_boolean = false
    }


    // Create containers menu:
    def setupFileSystemUI(): Unit = {
      var exitSetup = false
      var latencies = Seq.empty[Int]

      while (!exitSetup) {
        println("(!) Creating containers menu - Choose an event:")
        println("(-) 1: Add a container")
        println("(-) 0: Stop creating containers")
        val menu_integer = get_user_choice()
        if (menu_integer == 0) {
          if (latencies.isEmpty) {
            println("(!) No container was created, returning to main menu")
          }
          else {
            fileSystem = FileSystem(latencies.length, latencies, Seq(0.0))//TODO
            manager = FileSuiteManager(fileSystem)
            numContainers = latencies.length
            for(i <- latencies.indices){
              println("(" + (i+1) + ") Created a container with latency: " + latencies(i) )
            }
            createdFileSystem = true
          }
          exitSetup = true
          println("(!) Left menu for creating containers")
        }
        else if (menu_integer == 1) {
          println("(!) Enter latency of new container:")
          latencies = latencies :+ get_user_choice()
        }
      }
    }

    // Create a suite menu:
    def createSuiteUI() : Unit = {
      println("(!) What is the ID of the created suite?")
      val suiteID = get_user_choice()
      println("(!) What is the R value of the suite?")
      val Rsuite = get_user_choice()
      println("(!) What is the W value of the suite?")
      val Wsuite = get_user_choice()
      var repWeights = Seq[Int]()
      println("(!) What are the weights of the representatives?")
      for (i <- 0 until numContainers){
        println("(!) What is the weight of the representative in container "+i+"?")
        var newWeight = get_user_choice()
        repWeights = repWeights :+ newWeight
      }
      val result = manager.create(suiteID, Rsuite, Wsuite, repWeights)
      result match {
        case Left(f) => println("Could not create file suite:\n" + f.reason)
        case Right(r) => {
          createdSuite = true
          println("Successfully created file suite with id " + suiteID)
        }
      }
    }

    // Read suite menu
    def readSuiteUI() : Unit = {
      println("(!) What is the ID of the file you want to read?")
      val suiteID = get_user_choice()
      val result = manager.read(suiteID)
      result match {
        case Left(f) => println("Could not read file suite:\n" + f.reason)
        case Right(r) => println("Content of file " + suiteID + " is " + r._1 + ". Latency: " + r._2)
      }
    }

    // Write a suite menu
    def writeSuiteUI(): Unit = {
      println("(!) What is the ID of the file you want to write to?")
      val suiteID = get_user_choice()
      println("(!) What is the content you want to write to file " + suiteID + "?")
      val newContent = get_user_choice()
      val result = manager.write(suiteID, newContent)
      result match {
        case Left(f) => println("Could not write to file suite:\n" + f.reason)
        case Right(r) => println("Successfully written " + newContent + " to file with id " + suiteID + ". Latency: " + r)
      }
    }

    def createFileSystemUI(): Unit = {
      while (!createdFileSystem && !exit_boolean) {
        println("(-)  1: Create File System")
        println("(-)  0: Quit")

        val menu_integer = get_user_choice()

        if (menu_integer == 0) {
          exit_boolean = true
          exitUI()
        }
        else if (menu_integer == 1) {
          setupFileSystemUI()
        }
        else {
          println("(E) Please enter a valid number!")
        }
      }
    }

    def createFileUI(): Unit = {
      while (!createdSuite && !exit_boolean) {
        println("(-)  1: Create file")
        println("(-)  0: Quit")

        val menu_integer = get_user_choice()

        if (menu_integer == 0) {
          exit_boolean = true
          exitUI()
        }
        else if (menu_integer == 1) {
          createSuiteUI()
        }
        else {
          println("(E) Please enter a valid number!")
        }
      }
    }

    def fullUI(): Unit = {
      while (!exit_boolean) {
        println("(-)  1: Create file")
        println("(-)  2: Read file")
        println("(-)  3: Write file")
        println("(-)  0: Quit")

        val menu_integer = get_user_choice()

        if (menu_integer == 0) {
          exit_boolean = true
          exitUI()
        }
        else if (menu_integer == 1) {
          createSuiteUI()
        }
        else if (menu_integer == 2) {
          readSuiteUI()
        }
        else if (menu_integer == 3) {
          writeSuiteUI()
        }
        else {
          println("(E) Please enter a valid number!")
        }
      }
    }

    // Main menu:
    while (!exit_boolean) {
      println("(!) Main menu - Choose an event:")
      if (!createdFileSystem) {
        createFileSystemUI()
      }
      else if (!createdSuite) {
        createFileUI()
      }
      else {
        fullUI()
      }
    }
    println("(!) User left UI")
    // end UI
  }
}
