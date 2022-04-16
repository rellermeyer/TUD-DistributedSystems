package Misc

import Communication.SocketData

import scala.io.Source

/**
 * The Resource Utilities handles file parsing.
 */
object ResourceUtilities {

  private val nodeList: Map[Int, (String, Int)] = parseNodeList()

  /**
   * Get the node list from the NodeList.txt file.
   * @return The list as Map with the Node ID as key and a Tuple of (IP, port) as value.
   */
  def getNodeList: Map[Int, (String, Int)] = nodeList

  /**
   * Get the SocketData of a Node based on the given Node ID.
   * @return A SocketData object containing the Node ID, IP and port.
   */
  def getNodeSocketData(ID: Int): SocketData = {
    new SocketData(ID, nodeList(ID)._1, nodeList(ID)._2)
  }

  /**
   * Parse the NodeList.txt file and store it in memory.
   * @return The node list as Map
   */
  private def parseNodeList(): Map[Int, (String, Int)] = {
    val filename = "NodeList.txt"
    val nodeList = Source.fromResource(filename).getLines
      .map(line => {
        val Array(id, ip, port, _*) = line.split(' ')
        id.toInt -> (ip, port.toInt)
      }).toMap
    nodeList
  }
}
