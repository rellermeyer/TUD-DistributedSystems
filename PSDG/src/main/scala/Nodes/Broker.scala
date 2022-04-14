package Nodes

import Messaging.GuaranteeType._
import Messaging._
import Routing.RoutingTable

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * The broker knows its own ID and the IDs of its neighbours in the broker network.
 * The brokers are used for storing the publish/subscribe routing tables and for
 * forwarding the messages send by publishers and subscribers.
 */
class Broker(override val ID: Int, val NB: List[Int]) extends Node(ID) {

  private val lastHops = mutable.Map[(String, (Int, Int)), Int]()
  private val SRT = new RoutingTable() // The subscription routing table
  private val PRT = new RoutingTable() // The publication routing table
  private val IsActive = mutable.Map[(Int, Int), Boolean]()
  private val promiseList = mutable.Map[(Int, Int), Message]()
  private val timestamps: mutable.Map[(String, (Int, Int),Int),Long] = mutable.Map[(String, (Int, Int),Int),Long]()

  // The promise list feature enables the edge broker to store subscriptions for which no advertisements are known yet.
  // It wil forward the messages when a matching advertisement arrives.
  private val promiseListActive = false

  // The time-out limit is by default set to 10 minutes.
  private val timeoutLimit = 600000

  /**
   * Modify the local state for advertisements
   */
  def processAdvertisement(content: Advertise): Unit = {
    if (!advertisementList.contains(content.advertisement.ID)) {
      advertisementList += (content.advertisement.ID -> Advertisement(content.advertisement.ID, content.advertisement.pClass, content.advertisement.pAttributes))
    }
  }

  /**
   * Modify the local state for un-advertisements
   */
  def clearAdvertisement(content: Unadvertise): Unit = {
    if (advertisementList.contains(content.advertisement.ID)) {
      advertisementList -= content.advertisement.ID
    }
  }

  /**
   * Modify the local state for subscriptions
   */
  def processSubscription(content: Subscribe): Unit = {
    if (!subscriptionList.contains(content.subscription.ID)) {
      subscriptionList += (content.subscription.ID -> Subscription(content.subscription.ID, content.subscription.pClass, content.subscription.pAttributes))
    }
  }

  /**
   * Modify the local state for un-subscriptions
   */
  def clearSubscription(content: Unsubscribe): Unit = {
    if (subscriptionList.contains(content.subscription.ID)) {
      subscriptionList -= content.subscription.ID
    }
  }

  /**
   * Receive an advertisement message, update the local state and flood it to next hops.
   */
  def receiveAdvertisement(message: Message): Unit = {
    println("Receiving Advertisement from " + message.sender.ID)

    val content: Advertise = message.content.asInstanceOf[Advertise]
    val messageType: String = content.getClass.getName
    val lastHop: Int = message.sender.ID
    val a: (Int, Int) = content.advertisement.ID

    lastHops += ((messageType, a) -> lastHop)

    // Add new route to the subscription routing table
    SRT.addRoute(a, lastHop, content.advertisement.pClass, content.advertisement.pAttributes)

    // Exclude the last hop as possible next hop
    val nextHops: List[Int] = NB diff List(lastHop)

    // Flood the advertisement over all valid next hops
    for (hop <- nextHops) {
      println("Forwarding Advertisement to " + hop)
      sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop) // Flood to next hops
    }

    processAdvertisement(content)

    // If the promise feature is active, check the list when a new advertisement arrives
    if (promiseListActive) {
      val promises: List[Message] = findPromiseMatch(content.advertisement)
      for (item <- promises) {
        println("[PROMISE] Forwarding subscription to " + item.destination)
        receiveSubscription(item)
        promiseList -= item.content.asInstanceOf[Subscribe].subscription.ID
      }
    }

    // If the guarantee type is ACK, then send a ACK message back via the last hop
    if (content.guarantee == ACK) {
      if (nextHops.isEmpty) { // Reached an edge broker
        sendACK(messageType, a, lastHop)
      } else {
        for (hop <- nextHops) {
          startAckTimer(messageType, a, hop)
          ACKS += ((messageType, a, hop) -> false)
        }
      }
    }
  }

  /**
   * Receive an un-advertisement message, update the local state and flood it to next hops.
   */
  def receiveUnadvertisement(message: Message): Unit = {
    println("Receiving Unadvertisement from " + message.sender.ID)

    val content: Unadvertise = message.content.asInstanceOf[Unadvertise]
    val messageType: String = content.getClass.getName
    val lastHop: Int = message.sender.ID
    val a: (Int, Int) = content.advertisement.ID

    lastHops += ((messageType, a) -> lastHop)

    // Delete the route from the subscription routing table
    SRT.deleteRoute(a)

    // Exclude the last hop as possible next hop
    val nextHops: List[Int] = NB diff List(lastHop)

    // Flood the un-advertisement over all valid next hops
    for (hop <- nextHops) {
      println("Forwarding Unadvertisement to " + hop)
      sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop) // Flood to next hops
    }

    clearAdvertisement(content)

    // If the guarantee type is ACK, then send a ACK message back via the last hop
    if (content.guarantee == ACK) {
      if (nextHops.isEmpty) { // Reached an edge broker
        sendACK(messageType, a, lastHop)
      } else {
        for (hop <- nextHops) {
          startAckTimer(messageType, a,hop)
          ACKS += ((messageType, a, hop) -> false)
        }
      }
    }
  }

  /**
   * Receive a subscription message, update the local state and send it to matching next hops.
   */
  def receiveSubscription(message: Message): Unit = {
    println("Receiving Subscription")

    val content: Subscribe = message.content.asInstanceOf[Subscribe]
    val messageType: String = content.getClass.getName
    val lastHop: Int = message.sender.ID
    val s: (Int, Int) = content.subscription.ID

    lastHops += ((messageType, s) -> lastHop)

    // Find the matches from the subscription routing table
    val advs: List[(Int, Int)] = SRT.findMatch(content.subscription)

    var nextHopsSet: Set[Int] = Set[Int]()

    // If the promise feature is active and no matching advertisements are found then store the subscription in the promise list
    if (advs.isEmpty && promiseListActive) {
      promiseList += (content.subscription.ID -> message)
      println("[PROMISE] Subscription added to promise buffer: " + promiseList)
      return
    }
    else { // Else create the next hop set from matching routing entries
      for (ad <- advs) {
        val candidateDestination = SRT.getRoute(ad)._1
        if (NB.contains(candidateDestination)) {
          nextHopsSet += candidateDestination
        }
      }
    }

    val nextHops: List[Int] = nextHopsSet.toList diff List(lastHop)

    // Add new route to the publication routing table
    PRT.addRoute(s, lastHop, content.subscription.pClass, content.subscription.pAttributes)


    content.guarantee match {
      // If the guarantee type is ACK, then send a ACK message back via the last hop and forward the subscription to the matching routing entries
      case ACK =>
        IsActive += (s -> false)
        if (nextHops.isEmpty) {
          IsActive += (s -> true)
          sendACK(messageType, s, lastHop)
        } else {
          for (hop <- nextHops) {
            println("Forwarding Subscription to " + hop)
            sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop)
          }
          for (hop <- nextHops) {
            ACKS += ((messageType, s, hop) -> false)
            startAckTimer(messageType, s,hop)
          }
        }
      case NONE =>
        // If the guarantee type is NONE, then just forward the subscription to matching routing entries
        IsActive += (s -> true)
        for (hop <- nextHops) {
          println("Forwarding Subscription to " + hop)
          sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop)
        }
    }
    processSubscription(content)
  }

  /**
   * Receive an un-subscription message, update the local state and send it to matching next hops.
   */
  def receiveUnsubscription(message: Message): Unit = {
    println("Receiving Unsubscription")

    val content: Unsubscribe = message.content.asInstanceOf[Unsubscribe]
    val messageType: String = content.getClass.getName
    val lastHop: Int = message.sender.ID
    val s: (Int, Int) = content.subscription.ID

    lastHops += ((messageType, s) -> lastHop)

    // Find the matches from the subscription routing table
    val advs: List[(Int, Int)] = SRT.findMatch(content.subscription)
    var nextHopsSet: Set[Int] = Set[Int]()

    // Create the next hop set from matching routing entries
    for (ad <- advs) {
      val candidateDestination = SRT.getRoute(ad)._1
      if (NB.contains(candidateDestination)) {
        nextHopsSet += candidateDestination
      }
    }

    val nextHops: List[Int] = nextHopsSet.toList diff List(lastHop)

    // Remove the route from the publication routing table
    PRT.deleteRoute(s)

    content.guarantee match {
      case ACK =>
        // If the guarantee type is ACK, then send a ACK message back via the last hop and forward the unsubscription to the matching routing entries
        if (nextHops.isEmpty) {
          IsActive += (s -> false)
          sendACK(messageType, s, lastHop)
        } else {
          for (hop <- nextHops) {
            println("Forwarding Unsubscription to " + hop)
            sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop)
          }
          for (hop <- nextHops) {
            ACKS += ((messageType, s, hop) -> false)
            startAckTimer(messageType, s,hop)
          }
        }
      case NONE =>
        // If the guarantee type is NONE, then just forward the unsubscription to matching routing entries
        IsActive += (s -> false)
        for (hop <- nextHops) {
          println("Forwarding Unsubscription to " + hop)
          sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop)
        }
    }
    clearSubscription(content)
  }

  /**
   * Receive a publication message, update the local state and send it to matching next hops.
   */
  def receivePublication(message: Message): Unit = {
    println("Receiving Publication from " + message.sender.ID)

    val content: Publish = message.content.asInstanceOf[Publish]
    val messageType: String = content.getClass.getName
    val lastHop: Int = message.sender.ID
    val p: (Int, Int) = content.publication.ID

    lastHops += ((messageType, p) -> lastHop)

    // Find the matches from the publication routing table
    val subs: List[(Int, Int)] = PRT.findMatch(content.publication)

    var nextHopsSet: Set[Int] = Set[Int]()

    // Create the next hop set from matching routing entries
    for (s <- subs) {
      if (IsActive.contains(s)) {
        val candidateDestination = PRT.getRoute(s)._1
        nextHopsSet += candidateDestination
      }
    }

    val nextHops: List[Int] = nextHopsSet.toList diff List(lastHop)

    // Forward the publications to the matching routing entries
    for (hop <- nextHops) {
      println("Forwarding Publication to " + hop)
      sendMessage(new Message(getMessageID(), SocketData, hop, content, getCurrentTimestamp), hop) // Flood to next hops
    }

    // If the guarantee type is ACK, then send a ACK message back if the edge broker of the receiving client is reached.
    if (content.guarantee == ACK) {
      if ((nextHops intersect NB).isEmpty) {
        sendACK(messageType, p, lastHop)
      } else { // For any other broker start the ACK time-out timer.
        for (hop <- nextHops) {
          if (NB.contains(hop)) {
            ACKS += ((messageType, p, hop) -> false)
            startAckTimer(messageType, p,hop)
          }
        }
      }
    }
  }

  /**
   * Send an acknowledgement message
   */
  def sendACK(messageType: String, ID: (Int, Int), lastHop: Int): Unit = {
    println("Sending " + messageType +" Ack Response to " + lastHop)

    val ACK = AckResponse(messageType, ID)
    sendMessage(new Message(getMessageID(), SocketData, lastHop, ACK, getCurrentTimestamp), lastHop)
  }

  /**
   * Receive an acknowledgement message. If it has timed-out, ignore this message.
   */
  def receiveACK(message: Message): Unit = {
    println("Receiving Ack Response from " + message.sender.ID)

    val ACK = message.content.asInstanceOf[AckResponse]
    val messageType = ACK.messageType
    val senderID = message.sender.ID

    if (timestamps.contains(messageType, ACK.ID, senderID) && !ACK.timeout) {

      println("Processing of ACK " + ACK.ID + " took: " + (getCurrentTimestamp - timestamps(messageType, ACK.ID, senderID)) + "ms")

      timestamps -= ((messageType, ACK.ID, senderID))

      ACKS += ((messageType, ACK.ID, message.sender.ID) -> true)

      if (receivedAllPendingACKS(messageType, ACK.ID, message.sender.ID)) {

        if (ACK.messageType == "Messaging.Subscribe") {
          IsActive += (ACK.ID -> true)
        }
        if (ACK.messageType == "Messaging.Unsubscribe") {
          IsActive += (ACK.ID -> false)
        }

        val destinationID = lastHops(messageType, ACK.ID)
        println("Sending " + messageType + "  ACK Response to " + destinationID)
        sendMessage(new Message(getMessageID(), SocketData, destinationID, ACK, getCurrentTimestamp), destinationID)
      }
    }
  }

  /**
   * Check if all neighbours have sent an acknowledgement. Only forward the acknowledgement if this is true.
   */
  def receivedAllPendingACKS(messageType: String, ACKID: (Int, Int), senderID: Int): Boolean = {
    for (neighbourBroker <- NB diff List(senderID)) {
      if (ACKS.contains(messageType, ACKID, neighbourBroker) && !ACKS(messageType, ACKID, neighbourBroker)) {
        return false
      }
    }
    true
  }

  /**
   * Implementation for the promise list feature. For every incoming advertisement we test whether
   * it matches any of the promised subscriptions by class and attributes.
   * @return The list of found matches.
   */
  def findPromiseMatch(advertisement: Advertisement): List[Message] = {
    val matches: ListBuffer[Message] = ListBuffer[Message]()

    // Loop over all entries of the promise list
    for (item <- promiseList) {

      val promisedMessage = item._2.content.asInstanceOf[Subscribe]

      val pClass = promisedMessage.subscription.pClass
      val pAttributes = promisedMessage.subscription.pAttributes
      val valueAdvertisement = advertisement.pAttributes._2
      val valueBoundSubscription = pAttributes._2
      val operatorAdvertisement = advertisement.pAttributes._1
      val operatorSubscription = pAttributes._1

      // If the class of the advertisement matches a promise entry, proceed by checking the attributes
      if (pClass.equals(advertisement.pClass)){

        var validAdvertisement = false

        if (operatorSubscription.equals(operatorAdvertisement)) {

          validAdvertisement = operatorSubscription match {
            case "gt" => valueAdvertisement >= valueBoundSubscription
            case "lt" => valueAdvertisement <= valueBoundSubscription
            case "e" => valueAdvertisement == valueBoundSubscription
            case "ne" => valueAdvertisement == valueBoundSubscription
          }
        }

        // If the class and attributes matches then store it in the found matches list
        if (validAdvertisement) {
          matches += item._2
        }
      }
    }
    matches.toList
  }

  /**
   * Start the ACK timer. The ACK timer is started on another thread which periodically checks
   * the current time against the message timestamp. If it has not received a response within
   * the defined time-out limit, then a timeout ACK is sent.
   */
  def startAckTimer(messageType: String, ID: (Int, Int), hop: Int): Unit = {
    timestamps += ((messageType, ID, hop) -> getCurrentTimestamp)

    val t1 = new Thread(new Runnable() {
      override def run(): Unit = {
        while (true) {
          // Check every 0.2s if it is still below the time-out limit.
          Thread.sleep(200)
          try {
            // If the timestamp does not exist anymore it means that an ACK has been received and we can abort the timer.
            if (!timestamps.contains(messageType, ID, hop)) {
              return
            }
            // If the time-out limit is exceeded, remove the timestamp and send a time-out ack response.
            if (getCurrentTimestamp - timestamps(messageType, ID, hop) > timeoutLimit) {
              timestamps -= ((messageType, ID, hop))
              sendTimeOut(AckResponse(messageType, ID, timeout = true))
            }
          } catch {
            case _ =>
          }
        }
      }
    })

    t1.start()
  }

  /**
   * If the ACK has timed-out, send modify the local state of the broker and forward the timed-out ACK.
   */
  def sendTimeOut(ACK: AckResponse): Unit = {
    println("[TIMEOUT] " + ACK.ID + " " + ACK.messageType)
    val destinationID = lastHops(ACK.messageType, ACK.ID)

    // Check the type of the ACK message and modify local state accordingly
    ACK.messageType match {
      case "Messaging.Subscribe" =>
        PRT.deleteRoute(ACK.ID)
        subscriptionList -= ACK.ID
      case "Messaging.Advertise" =>
        SRT.deleteRoute(ACK.ID)
        advertisementList -= ACK.ID
      case _ =>
    }

    // Send the TimeOut message
    sendMessage(new Message(getMessageID(), SocketData, destinationID, ACK, getCurrentTimestamp), destinationID)
  }

  /**
   * First execute the shared execute method from the Node class.
   * Then it opens a ReceiverSocket and actively listen for messages.
   */
  override def execute(): Unit = {
    super.execute()
    super.startReceiver()

    while (true) {

      // Simulate random network delay between 10 and 50 ms
      val randomNetworkDelay = 10 + randomGenerator.nextInt(40)
      Thread.sleep(randomNetworkDelay)

      // While there a messages in the queue, parse the messages.
      while (!receiver.isQueueEmpty) {

        val message = receiver.getFirstFromQueue()

        writeFileMessages("received", message)

        message.content match {
          case _ : Advertise => receiveAdvertisement(message)
          case _ : Unadvertise => receiveUnadvertisement(message)
          case _ : Subscribe => receiveSubscription(message)
          case _ : Publish => receivePublication(message)
          case _ : Unsubscribe => receiveUnsubscription(message)
          case _ : AckResponse => receiveACK(message)
          case _ =>
        }
      }
    }
  }
}
