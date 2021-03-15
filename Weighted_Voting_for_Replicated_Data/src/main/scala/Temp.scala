import FileSystem.{Container, DistributedSystem, Representative}
import VotingSystem.FileSuiteManager

object Temp {
  def main(args: Array[String]): Unit = {

    //Create a test "distributed" file system
    val fileSystem = DistributedSystem(0.0)
    /*fileSystem.createContainers(Seq(0, 2, 1, 4, 3))
    fileSystem.createSuite(1, 1, 1, Seq(0, 2, 1, 4, 3))

    //This demonstrates that you can gather a correct response,
    //and that find latest picks the first correct option in the list
    //Note that response also contains the prefixes, but those are not printed
    var response = fileSystem.collectSuite(1)
    println(response)
    println(response.findLatest())
    println(response.findReadQuorum(30, 0))

    //This demonstrates that findLatest correctly returns the rep that has been updated
    fileSystem.writeSuite(Seq(2), 1, 1)
    response = fileSystem.collectSuite(1)
    println(response.findLatest())

    println(response.findReadQuorum(30, 1))*/

    //    UI:
    var exit_boolean = true
    var user_input_tutorial = true
    var menu_integer = -1
    var input_integer = -1


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
    def createContainersUI() : Unit = {
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
            fileSystem.createContainers(latencies) // remove latencies.size here to make compatable with newer version.
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


    // Create suite menu
    def createSuiteUI() : Unit = {
      println("(!) What is the ID of the created suite?")
      var suiteID = get_user_choice()
      println("(!) What is the R value of the suite?")
      var Rsuite = get_user_choice()
      println("(!) What is the W value of the suite?")
      var Wsuite = get_user_choice()
      var repWeights = Seq[Int]()
      println("(!) What are the weights of the representatives?")
      //      for( i <- 0 to DistributedSystem._containers.size){                     // idea here is to call all containers and ask a weight for each container/representative
      //        println("(!) What is the weight of the representative in container "+i+"?")
      //        repWeights = repWeights :+ get_user_choice()
      //      }
      fileSystem.createSuite(suiteID, Rsuite, Wsuite, Seq(1, 2, 3, 4, 5))       // Put repWeights instead of Seq(1, 2, 3, 4, 5)
    }


    //    println(fileSystem.collectSuite(1))
    //    println(fileSystem.readSuite(0,1))

    // Collect suite
    def collectSuiteUI() : Unit = {
      var suiteID = -1
      println("(!) What is the ID of the suite you want to collect?")
      suiteID = get_user_choice()
      if (suiteID == -1){
        println("(E) No ID was taken as input for collectSuiteUI!")
        exitUI()
      } else {
        println("(!) Collected suite "+suiteID+":")
        println(fileSystem.collectSuite(suiteID))
      }
    }

    // Read suite
    def readSuiteUI() : Unit = {
      var containerID = -1
      println("(!) What is the ID of the container you want to read?")
      containerID = get_user_choice()
      if (containerID == -1){
        println("(E) No container ID was taken as input for readSuiteUI!")
        exitUI()
      } else {
        var suiteID = -1
        if (containerID == -1){
          println("(E) No suite ID was taken as input for readSuiteUI!")
          exitUI()
        } else {
          println("(!) What is the ID of the suite you want to read?")
          suiteID = get_user_choice()
          println(fileSystem.readSuite(containerID, suiteID))
        }
      }
    }

    // Main menu:
    while ( { exit_boolean }) {
      println("(!) Main menu - Choose an event:")
      println("(-)  1: Create containers")
      println("(-)  2: Create suite")
      println("(-)  3: Collect suite")
      println("(-)  4: Read suite")
      println("(-)  0: Quit")
      menu_integer = get_user_choice()
      if (menu_integer == 0){
        exitUI()
      } else if (menu_integer == 1){
        createContainersUI()
      } else if (menu_integer == 2){
        createSuiteUI()
      } else if (menu_integer == 3){
        collectSuiteUI()
      } else if (menu_integer == 4){
        readSuiteUI()
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
