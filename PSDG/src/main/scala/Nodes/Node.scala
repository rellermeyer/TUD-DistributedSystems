package Nodes

import Communication.{ReceiverSocket, SenderSocket, SocketData}
import Messaging.{Advertisement, Message, Subscription}
import Misc.ResourceUtilities

import org.apache.commons.net.ntp.TimeStamp
import scala.collection.mutable
import scala.util.Random
import scala.language.implicitConversions
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import java.io.{File, FileWriter}

/**
 * The abstract node class can be initialized as broker and client node.
 * The shared methods are defined under this abstract node class.
 */
abstract class Node(val ID: Int) {

  protected val SocketData: SocketData = ResourceUtilities.getNodeSocketData(ID)
  protected val receiver: ReceiverSocket = new ReceiverSocket(SocketData)
  protected val sender: SenderSocket = new SenderSocket()

  protected val randomGenerator: Random = Random
  protected val counters: mutable.Map[String, Int] = mutable.Map[String, Int]()
  protected val ACKS: mutable.Map[(String, (Int, Int), Int), Boolean] = mutable.Map[(String, (Int, Int), Int), Boolean]()
  protected val subscriptionList: mutable.Map[(Int, Int), Subscription] = mutable.Map[(Int, Int), Subscription]()
  protected val advertisementList: mutable.Map[(Int, Int), Advertisement] = mutable.Map[(Int, Int), Advertisement]()
  protected val messageSaveThreshold = 100

  /**
   * Get the IP address of the node
   * @return IP as a String
   */
  def getNodeIP: String = {
    SocketData.address
  }

  /**
   * Get the port of the node
   * @return Port as Integer
   */
  def getNodePort: Int = {
    SocketData.port
  }

  /**
   * Get an incremented message ID
   * @return Unique tuple of (SenderID, MessageID)
   */
  def getMessageID(): (Int, Int) = {
    counters += ("Message" -> (counters("Message") + 1))
    (ID, counters("Message"))
  }

  /**
   * Get the current timestamp as apache ntp timestamp
   * @return The timestamp as 64-bit signed integer.
   */
  def getCurrentTimestamp: Long = {
    TimeStamp.getCurrentTime.getTime
  }

  /**
   * Send a message to a Destination ID. This is a high-level wrapper for the
   * sender Socket. The destination socket is fetched from Resource Utilities
   * after which the message is logged and send over the sender socket.
   */
  def sendMessage(message: Message, DestinationID: Int): Unit = {
    val DestinationSocketData = ResourceUtilities.getNodeSocketData(DestinationID)
    writeFileMessages("sent", message)
    sender.sendMessage(message, DestinationSocketData.address, DestinationSocketData.port)
  }

  /**
   * This method is used for writing message activity to the local log file.
   * The log file is used later for analyzing the behaviour of the node.
   */
  def writeFileMessages(option:String,message: Message): Unit = {

    val location = "/tmp/" + ID.toString + "/" + option + "/"
    val directory = new File(String.valueOf(location))

    if (!directory.exists) {
      directory.mkdirs()
    }

    implicit val formats: DefaultFormats.type = DefaultFormats

    var filename= ""

    if (option == "received") {
      counters += ("SavedMessagesReceived" -> (counters("SavedMessagesReceived") + 1))
      filename = location + option + "_" + (counters("SavedMessagesReceived") / messageSaveThreshold).toString + ".ndjson"
    } else {
      counters += ("SavedMessagesSent" -> (counters("SavedMessagesSent") + 1))
      filename = location + option + "_" + (counters("SavedMessagesSent") / messageSaveThreshold).toString + ".ndjson"
    }
    val jsonString = write(message)
    val fileWriter = new FileWriter(filename, true)
    fileWriter.write(jsonString + "\n")
    fileWriter.close()
  }

  /**
   * Open the receiver socket and keep it open in a separate thread.
   */
  def startReceiver(): Unit = {
    val t = new Thread(receiver)
    t.start()
  }

  /**
   * Initialize all message counters.
   */
  def initializeCounters(): Unit = {
    counters += ("Message" -> 1)
    counters += ("Advertisements" -> 1)
    counters += ("Subscriptions" -> 1)
    counters += ("Publications" -> 1)
    counters += ("SavedMessagesSent" -> 0)
    counters += ("SavedMessagesReceived" -> 0)
  }

  /**
   * When the node is started, first a random seed is initialized.
   * This seed is based on the ID of the node to make sure every node uses a unique seed.
   * Then, the message counters are initialized.
   */
  def execute(): Unit = {
    val randomSeed = 4
    randomGenerator.setSeed(ID + 2 * randomSeed)
    initializeCounters()
  }
}
