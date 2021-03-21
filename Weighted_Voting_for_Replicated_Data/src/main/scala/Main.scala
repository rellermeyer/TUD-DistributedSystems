import FileSystem.DistributedSystem
import VotingSystem.FileSuiteManager

object Main {
  def main(args: Array[String]): Unit = {

    val numContainers: Int = 5
    val latencies: Seq[Int] = Seq(1, 2, 3, 4, 5)
    val failureProb: Double = 0.0

    val fileSystem = DistributedSystem(numContainers, latencies, failureProb)
    val manager = FileSuiteManager(fileSystem)

    //    UI (user interface):
    var exit_boolean = true
    var user_input_tutorial = true
    var menu_integer = -1
    var input_integer = -1

    // Retrieving input from the user
    def get_user_choice () : Int = {
      if (user_input_tutorial == true) {
        println("(!) Your input: (Keep in mind that for all user inputs integers are required, else the system will crash!)")
        user_input_tutorial = false
      }
      input_integer = scala.io.StdIn.readInt()
      return input_integer
    }


    def exitUI() : Unit = {
      println("(!) Leaving UI")
      exit_boolean = false
    }


    // Create containers menu:
    def createContainersUI(): Unit = {
      var exit_createContainersUI = true
      var latencies = Seq[Int]()
      while ( { exit_createContainersUI }) {
        println("(!) Creating containers menu - Choose an event:")
        println("(-) 1: Add a container")
        println("(-) 0: Stop creating containers")
        menu_integer = get_user_choice()
        if (menu_integer == 0){
          if (latencies.isEmpty){
            println("(!) No container was created, returning to main menu")
          } else {
            //fileSystem.createContainers(latencies) // remove latencies.size here to make compatable with newer version.
            var i = 0
            for( i <- 0 to latencies.size-1){
              println("("+(i+1)+") Created a container with latency: " + latencies(i) )
            }
          }
          exit_createContainersUI = false
          println("(!) Left menu for creating containers")
        } else if (menu_integer == 1){
          println("(!) Latency of container:")
          latencies = latencies :+ get_user_choice()
        }
      }
    }


    // Create a suite menu:
    def createSuiteUI() : Unit = {
      println("(!) What is the ID of the created suite?")
      var suiteID = get_user_choice()
      println("(!) What is the R value of the suite?")
      var Rsuite = get_user_choice()
      println("(!) What is the W value of the suite?")
      var Wsuite = get_user_choice()
      var repWeights = Seq[Int]()
      println("(!) What are the weights of the representatives?")
      for (i <- 0 until numContainers){                     // idea here is to call all containers and ask a weight for each container/representative
        println("(!) What is the weight of the representative in container "+i+"?")
        var newWeight = get_user_choice()
        repWeights = repWeights :+ newWeight
      }
      val result = manager.create(suiteID, Rsuite, Wsuite, repWeights)       // Put repWeights instead of Seq(1, 2, 3, 4, 5)
      result match {
        case Left(f) => println("Could not create file suite:\n" + f.reason)
        case Right(r) => println("Successfully created file suite with id " + suiteID)
      }
    }


    // Read suite menu
    def readSuiteUI() : Unit = {
        var suiteID = -1
          println("(!) What is the ID of the file you want to read?")
          suiteID = get_user_choice()
          val result = manager.read(suiteID)
          result match {
            case Left(f) => println("Could not read file suite:\n" + f.reason)
            case Right(r) => println("Content of file " + suiteID + " is " + r._1 + ". Latency: " + r._2)
          }
    }

    // Write a suite menu
    def writeSuiteUI(): Unit = {
      var suiteID = -1
      var newContent = -1
      println("(!) What is the ID of the file you want to write to?")
      suiteID = get_user_choice()
      println("(!) What is the content you want to write to file " + suiteID + "?")
      newContent = get_user_choice()
      val result = manager.write(suiteID, newContent)
      result match {
        case Left(f) => println("Could not write to file suite:\n" + f.reason)
        case Right(r) => println("Successfully written " + newContent + " to file with id " + suiteID + ". Latency: " + r)
      }
    }

    // Main menu:
    while ( { exit_boolean }) {
      println("(!) Main menu - Choose an event:")
      println("(-)  1: Create file")
      println("(-)  2: Read file")
      println("(-)  3: Write file")
      println("(-)  0: Quit")
      menu_integer = get_user_choice()
      if (menu_integer == 0){
        exitUI()
      } else if (menu_integer == 1){
        createSuiteUI()
      } else if (menu_integer == 2){
        readSuiteUI()
      } else if (menu_integer == 3){
        writeSuiteUI()
      }

      else if (menu_integer == -1){
        println("(E) User input is missing!")
        exitUI()
      } else {
        println("(E) Something went wrong!")
        exitUI()
      }
    }
    println("(!) User left UI")
    // end UI
  }
}
