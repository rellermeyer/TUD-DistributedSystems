package Nodes

import Messaging.GuaranteeType._
import Messaging._
import Nodes.ClientType.{ClientType, PUBLISHER, SUBSCRIBER}

import scala.collection.mutable

/**
 * The client can be initialized as either publisher or subscriber.
 * It knows its own ID and the ID of the edge broker it wants to communicate with.
 */
class Client(override val ID: Int, val brokerID: Int, val mode: ClientType) extends Node(ID) {

  private val publicationList = mutable.Map[(Int, Int), Publication]()
  private val publicationsReceivedList = mutable.Map[(Int, Int), Publication]()
  private val waitingForACK = mutable.Map[(String, (Int, Int)),Int]()

  protected var numberOfSimulations = 0
  protected var startDelayBaseline = false

  // The settings below can be adjusted for the simulations.
  protected var simulationLimit = 1000
  protected var guaranteeType: Messaging.GuaranteeType.Value = ACK

  /**
   * Publisher method.
   * Send an advertisement to the edge broker.
   */
  def sendAdvertisement(pClass: String, pAttributes: (String, Int), guarantee: GuaranteeType): Unit = {
    println("Sending Advertisement to " + brokerID)

    val adID: (Int, Int) = (ID, counters("Advertisements"))
    val advertisement = Advertisement(adID, pClass, pAttributes)
    val content = Advertise(advertisement, guarantee)

    // Send the message with the selected content under a specific guarantee
    sendMessage(new Message(getMessageID(), SocketData, brokerID, content, getCurrentTimestamp), brokerID)

    // Modify local state
    advertisementList += (adID -> advertisement)
    if (guarantee == ACK) waitingForACK += ((content.getClass.getName, adID) -> 1)
    counters += ("Advertisements" -> (counters("Advertisements") + 1))
  }

  /**
   * Publisher method.
   * Send an un-advertisement to the edge broker.
   */
  def sendUnadvertisement(advertisement: Advertisement, guarantee: GuaranteeType): Unit = {
    println("Sending Unadvertisement to " + brokerID)

    val content = Unadvertise(advertisement, guarantee)

    // Send the message with the selected content under a specific guarantee
    sendMessage(new Message(getMessageID(), SocketData, brokerID, content, getCurrentTimestamp), brokerID)

    // Modify local state
    advertisementList -= advertisement.ID
    if (guarantee == ACK) waitingForACK += ((content.getClass.getName, content.advertisement.ID) -> 1)
  }

  /**
   * Subscriber method.
   * Send a subscription to the edge broker.
   */
  def sendSubscription(pClass: String, pAttributes: (String, Int), guarantee: GuaranteeType): Unit = {
    println("Sending Subscription to " + brokerID)

    val subID: (Int, Int) = (ID, counters("Subscriptions"))
    val subscription = Subscription(subID, pClass, pAttributes)
    val content = Subscribe(subscription, guarantee)

    // Send the message with the selected content under a specific guarantee
    sendMessage(new Message(getMessageID(), SocketData, brokerID, content, getCurrentTimestamp), brokerID)

    // Modify local state
    subscriptionList += (subID -> subscription)
    if (guarantee == ACK) waitingForACK += ((content.getClass.getName, subID) -> 1)
    counters += ("Subscriptions" -> (counters("Subscriptions") + 1))
  }

  /**
   * Subscriber method.
   * Send an un-subscription to the edge broker.
   */
  def sendUnsubscription(subscription: Subscription, guarantee: GuaranteeType): Unit = {
    println("Sending Unsubscription to " + brokerID)

    val content = Unsubscribe(subscription, guarantee)

    // Send the message with the selected content under a specific guarantee
    sendMessage(new Message(getMessageID(), SocketData, brokerID, content, getCurrentTimestamp), brokerID)

    // Modify local state
    subscriptionList -= subscription.ID
    if (guarantee == ACK) waitingForACK += ((content.getClass.getName, content.subscription.ID) -> 1)
  }

  /**
   * Publisher method.
   * Send a publication to the edge broker.
   */
  def sendPublication(pClass: String, pAttributes: (String, Int), pContent: Int, guarantee: GuaranteeType): Unit = {
    println("Sending Publication to " + brokerID)

    val pubID: (Int, Int) = (ID, counters("Publications"))
    val publication = Publication(pubID, pClass, pAttributes, pContent)
    val content = Publish(publication, guarantee)

    // Send the message with the selected content under a specific guarantee
    sendMessage(new Message(getMessageID(), SocketData, brokerID, content, getCurrentTimestamp), brokerID)

    // Modify local state
    publicationList += (pubID -> publication)
    if (guarantee == ACK) waitingForACK += ((content.getClass.getName, pubID) -> 1)
    counters += ("Publications" -> (counters("Publications") + 1))
  }

  /**
   * Subscriber method.
   * Receive a publication from the edge broker.
   */
  def receivePublication(message: Message): Unit = {
    val content: Publish = message.content.asInstanceOf[Publish]
    val pubID: (Int, Int) = content.publication.ID

    println("Receiving Publication from " + message.sender.ID + " with message: "
      + content.publication.pContent + " "
      + content.publication.pClass + " "
      + content.publication.pAttributes)

    // Modify local state
    publicationsReceivedList += (pubID -> content.publication)
  }

  /**
   * Receive an acknowledgement from a send message.
   * Only works if guarantee type ACK is selected for the simulation.
   */
  def receiveACK(message: Message): Unit = {
    println("Receiving ACK from " + message.sender.ID)

    val ACK = message.content.asInstanceOf[AckResponse]
    val messageType = ACK.messageType
    val ackCounter = waitingForACK((messageType, ACK.ID))

    // Modify local state
    waitingForACK -= ((messageType, ACK.ID))

    // If the ACK has timed-out, retry sending at most 4 times.
    if (ACK.timeout && ackCounter < 4) {
      waitingForACK += (messageType, ACK.ID) -> (ackCounter + 1)
      // Resend the message
      sendMessage(new Message(getMessageID(), SocketData, message.destination, message.content, getCurrentTimestamp), message.destination)
      println("Resending message " + ACK.ID + " " + messageType)
    } else if (ACK.timeout) {
      println("Ack counter surpassed: " + ACK.ID + " " + messageType)
    } else {
      println("Successfully installed " + ACK.ID + " " + messageType)
    }
  }

  /**
   * Define the simulation behaviour for publisher and subscriber nodes.
   */
  private def simulateClientBehaviour(): Unit = {
    val option = randomGenerator.nextInt(2000)

    var simulationExecution = false

    // Define the message content space of which we can randomly create a new message.
    val classes: List[String] = List("Apple", "Tesla", "Disney", "Microsoft")
    val operators: List[String] = List("gt", "lt")
    val randomOperator: String = operators(randomGenerator.nextInt(operators.length))
    val randomClass: String = classes(randomGenerator.nextInt(classes.length))
    val randomValue: Int = (randomGenerator.nextInt(20) * 5) + 5

    // Simulation behaviour if the node is a publisher
    if (mode == PUBLISHER) {
      option match {
        case x if advertisementList.size < 10 && x > 0 && x < 50 =>
          sendAdvertisement(randomClass, (randomOperator, randomValue), guaranteeType)
          simulationExecution = true
        case x if advertisementList.size > 9 && x > 0 && x < 100 =>
          if (guaranteeType == NONE && !startDelayBaseline) {
            Thread.sleep(10000)
            startDelayBaseline = true
          }
          val randomAdvertisementKey = advertisementList.keys.toList(randomGenerator.nextInt(advertisementList.size))
          val activeAdvertisement = advertisementList(randomAdvertisementKey)
          val valueAdvertisement = activeAdvertisement.pAttributes._2
          val operatorAdvertisement  = activeAdvertisement.pAttributes._1
          val offset = 101 - valueAdvertisement
          val publicationValue = operatorAdvertisement match {
            case "gt" => valueAdvertisement + randomGenerator.nextInt(offset)
            case "lt" => valueAdvertisement - randomGenerator.nextInt(valueAdvertisement)
          }
          if (guaranteeType!=ACK || !waitingForACK.contains(("Messaging.Advertise", activeAdvertisement.ID))) {
            sendPublication(activeAdvertisement.pClass, activeAdvertisement.pAttributes, publicationValue, guaranteeType)
            simulationExecution = true
          }
        case _ =>
      }
    }

    // Simulation behaviour if the node is a subscriber
    if (mode == SUBSCRIBER) {
      option match {
        case _ if subscriptionList.isEmpty =>
          sendSubscription(randomClass, (randomOperator, randomValue), guaranteeType)
          simulationExecution = true
        case _ =>
      }
    }

    if (simulationExecution) numberOfSimulations += 1

    if (numberOfSimulations == simulationLimit) println("Simulation Limit Reached. Now only listening to new messages.")
  }

  /**
   * First execute the shared execute method from the Node class.
   * Then it opens a ReceiverSocket and actively listen for messages.
   */
  override def execute(): Unit = {
    super.execute()
    super.startReceiver()

    // Safe wait period for brokers to start
    Thread.sleep(3000)

    // Safe wait period for advertisements and release a subscriber every second
    if (mode == SUBSCRIBER) {
      Thread.sleep(29000 + (ID * 1000))
    }

    while (true) {

      // Simulate random network delay between 10 and 50 ms
      val randomNetworkDelay = 10 + randomGenerator.nextInt(40)
      Thread.sleep(randomNetworkDelay)

      // While there a messages in the queue, parse the messages.
      while (!receiver.isQueueEmpty) {

        val message = receiver.getFirstFromQueue()

        writeFileMessages("received",message)

        message.content match {
          case _ : AckResponse => receiveACK(message)
          case _ : Publish => receivePublication(message)
          case _ =>
        }
      }

      // If we haven't completed the simulation yet, simulate some random client behaviour.
      if (numberOfSimulations < simulationLimit) simulateClientBehaviour()
    }
  }
}
