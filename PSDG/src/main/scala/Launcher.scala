import Nodes._
import Nodes.ClientType.ClientType

import scala.sys.exit

/**
 * Launcher class takes the input parameters of the jar and initializes the nodes.
 */
object Launcher extends Thread {

  private val usage =
    """
  Usage: Launcher
    --type    'client' or 'broker'
    --ID      ID of the Client

    In case of client:
      --BID   ID of Edge Broker
      --mode  'publisher' or 'subscriber'

    In case of broker:
      --NB   Neighbour broker ID's
  """


  /**
   * Initialize a node based on the input parameters
   * @return A node instance
   */
  def initializeNode(node_type: String, node_ID: Int, broker_ID: Int, node_mode: ClientType, NB: List[Int]): Node = {
    if (node_type == "client") {
      new Client(node_ID, broker_ID, node_mode)
    } else {
      new Broker(node_ID, NB)
    }
  }

  /**
   * Start the node instance
   */
  def startNode(node: Node): Unit = {
    println("Node is listening on " + node.getNodeIP + ":" + node.getNodePort)
    node.execute()
  }

  /**
   * The main method is the entry point of the software.
   * It is used for parsing the input parameters and creating and starting the nodes.
   */
  def main(args: Array[String]): Unit = {

    if (args.length == 0) {
      println(usage)
      exit(1)
    }

    // Initialize input parameters
    var arglist = args.toList
    var node_type = ""
    var node_mode: ClientType = null
    var node_ID = 0
    var broker_ID = 0
    var NB: List[Int] = null

    // Parse argument list
    while (arglist.nonEmpty) {
      arglist match {
        case "--type" :: value :: tail =>
          if (value == "broker" || value == "client") {
            node_type = value
            arglist = tail
          } else {
            println("Undefined Node Type")
            exit(1)
          }
        case "--ID" :: value :: tail =>
          node_ID = value.toInt
          arglist = tail
        case "--BID" :: value :: tail =>
          broker_ID = value.toInt
          arglist = tail
        case "--mode" :: value :: tail =>
          if (value == "publisher" || value == "subscriber") {
            node_mode = ClientType.values.find(_.toString.toLowerCase() == value.toLowerCase()).get
            arglist = tail
          } else {
            println("Undefined Node Type")
            exit(1)
          }
        case "--NB" :: value :: tail =>
          NB = value.split(',').map(_.toInt).toList
          arglist = tail
        case option =>
          println("Unknown option " + option)
          exit(1)
      }
    }

    val node = initializeNode(node_type, node_ID, broker_ID, node_mode, NB)

    println("Successfully initialized with the following start-up parameters:")
    println("Type: " + node_type)
    println("ID: " + node_ID)
    if (node_type == "broker") {
      println("Neighbour Brokers: " + NB)
    }

    if (node_type == "client") {
      println("BID: " + broker_ID)
      println("Mode: " + node_mode)
    }

    startNode(node)
  }
}
