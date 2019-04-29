package gia.core

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props, Timers}
import gia.core.SuperActor.AddNode

import scala.concurrent.duration._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

class SimActor(ip: String, port: Int, capacity: Int, superActor: ActorRef) extends Actor with ActorLogging with Timers {

  import SimActor._

  private val localContent = mutable.HashMap[String, GiaFile]()
  private val oneHopReplicationMap = new OneHopReplicationMap()
  private val node = new GiaNode(self, 0, 0, 0, 0)
  private var requestsReceivedThisMinute = 0

  // Priority queue, ordered by node capacity largest -> smallest, of all neighbors.
  val neighbors: mutable.PriorityQueue[GiaNode] = mutable.PriorityQueue[GiaNode]()

  timers.startPeriodicTimer(TriggerGrantTokenKey, TriggerGrantToken, 10.second)
  timers.startPeriodicTimer(TriggerReplicateToNeighborKey, TriggerReplicateToNeighbor, 10.second)

  override def preStart(): Unit = {
    super.preStart()
    //Capacity is measured in #requests being able to be processed in one minute
    node.capacity = capacity
    node.satisfaction = 0
    node.degree = 1

    if (ip == "first") {
      log.info("Initial node started.")
    }
    else {
      // Initialization to connect to existing node
      val firstNeighborConnectionString = "akka.tcp://gia@" + ip + ":" + port + "/user/giaActor"
      val firstNeighbor: ActorSelection = context.actorSelection(firstNeighborConnectionString)
      firstNeighbor ! ConnectionRequest
      log.info("Sent initial connection reqeust to " + firstNeighborConnectionString)
    }
    superActor ! AddNode(this.node)
  }

  override def postStop(): Unit = {
    super.postStop()
    // Tell all neighbors to purge your files and disconnect afterwards
    neighbors.foreach(n => {
      n.actor ! Replicate(List())
      n.actor ! DeleteNeighbor(this.node)
    })
  }

  override def receive: Receive = {

    case PrintNeighbors() => {
      println(this.node.actor.path + " has " + this.neighbors.size + " many neighbors")
    }

    case msg@Search(_, _, _, _) => {
      searchFile(msg)
    }

    case Found(foundFiles, actorRef) => {
      log.info("Found files: " + foundFiles + "\n" +
        "Found at node: " + actorRef.path)
    }

    case Replicate(neighborContent: List[GiaFile]) => {
      val neighbor = getNeighborNode(sender)
      if (neighbor != null) {
        replicate(neighborContent, neighbor)
      }
    }

    case ConnectionRequest => {
      val sendNeighbors = neighbors.clone()
      sendNeighbors.enqueue(this.node)
      sender ! ConnectionResponse(sendNeighbors)
      log.info("ConnectionRequest received from : " + sender.path + "\n" +
        "Responded with list of neighbors of size " + sendNeighbors.size)
    }

    case ConnectionResponse(neighbors) => {
      handleConnectionResponse(neighbors)
    }

    case AddFile(file: GiaFile) => {
      addFile(file)
    }

    case DeleteFile(file: GiaFile) => {
      deleteFile(file)
    }

    case AddNeighborAsk(node: GiaNode) => {
      // Add the desired neighbor to the neighbor list.
      // Sorting is done by the PQ.
      if (addNeighborCheck(node)) {
        sender ! AddNeighborConfirm(this.node)
        log.info("Granted neighbor access to: " + sender.path)
      }

    }

    case AddNeighborConfirm(node: GiaNode) => {
        addNeighbor(node)
    }

    case DeleteNeighbor(neighbor) => {
      deleteNeighbor(neighbor)
    }

    case ReceiveTokens(tokenCount, newDegree) => {
      for (neighbor <- neighbors) {
        if (neighbor.actor.equals(sender())) {
          neighbor.tokens += tokenCount
          neighbor.degree = newDegree
        }
      }
      log.info("Number of tokens received: " + tokenCount)
    }

    case TriggerGrantToken => {
      //Base rate of token generation equals its capacity
      var tokensToGive: Int = this.capacity - this.requestsReceivedThisMinute
      //If the load rises above 80% of the available capacity,
      //the generation rate is halved
      if(this.requestsReceivedThisMinute > this.capacity * 0.8) {
        tokensToGive /= 2
      }

      //Give out tokens proportionally to neighbors capacity
      var capacitySum = 0
      for (neighbor <- neighbors) {
        capacitySum += neighbor.capacity
      }
      if (capacitySum > 0) {
        for (neighbor <- neighbors) {
          val grantedTokens :Float =  neighbor.capacity.toFloat / capacitySum.toFloat * tokensToGive.toFloat
          neighbor.actor ! ReceiveTokens(grantedTokens.toInt, this.node.degree)
        }
      }


      this.requestsReceivedThisMinute = 0
    }

    case TriggerReplicateToNeighbor => {
      for (neighbor <- neighbors) {
        neighbor.actor ! Replicate(localContent.values.toList)
        log.debug("Replicated files to " + neighbor.actor.path)
      }
    }

    case Report() => {
      println(this.node.capacity + " - " + this.node.degree + " - " + this.node.satisfaction)
    }
  }

  private def searchFile(msg : Search) : Unit = {
    log.info("Searching for file: " + msg.keyword +" with time to live of " + msg.maxNodesVisited)

    // Check if we have visited the maximum number of nodes
    if (msg.maxNodesVisited <= msg.nodesVisited.size) {
      return
    }

    if (msg.nodesVisited.size > 1) {
      updateSatisfaction(msg.nodesVisited.toList(Random.nextInt(msg.nodesVisited.size)))
    }

    this.requestsReceivedThisMinute += 1

    // Add this actor to the set
    msg.nodesVisited.add(this.node.actor)

    // Priority queue, ordered by node capacity largest -> smallest, of all neighbors with the file.
    var neighborsWithFile = new mutable.ListBuffer[GiaNode]

    // Checking if the current actor has the file. If so, enqueue and broadcast.
    if(localContent.contains(msg.keyword)) {
      // Tell the origin node that this node has the file
      msg.origin ! Found(localContent.get(msg.keyword), this.node.actor)
      msg.origin ! SuperActor.Message(msg.nodesVisited.size, "foundLocal")
      log.info("File found locally: " + msg.keyword)
    }

    // Check if neighbors of 1-hop have file, and if so, add it to the queue.
    for(neighbor <- oneHopReplicationMap.getNodes(msg.keyword).getOrElse(mutable.HashSet.empty[GiaNode])) {
      neighborsWithFile += neighbor
    }

    // If the file has been found in a neighbor, search for it (1 hop replication).
    if(neighborsWithFile.nonEmpty) {
      // Iterate through neighbors and send the message through with decremented t-t-live.
      for(neighbor <- neighborsWithFile) {
        if (!msg.nodesVisited.contains(neighbor.actor)) {
          msg.nodesVisited += neighbor.actor
          // Tell the origin node that this neighbor has the file
          msg.origin ! Found(localContent.get(msg.keyword), neighbor.actor)
          msg.origin ! SuperActor.Message(msg.nodesVisited.size, "foundHop")
          log.info("File found via one-hop replication: " + msg.keyword)
        }
      }
    }

    // If nothing is found, send forward search query through iteration which breaks upon first viable neighbor.
    for(neighbor <- neighbors) {
      // Check if token count is valid, and if so, proceed to search and break out of loop.
      if(neighbor.tokens > 0 && !msg.nodesVisited.contains(neighbor.actor)) {
        // Decrement tokens for new call.
        neighbor.tokens -= 1

        // Send the message through to the highest capacity neighbor.
        neighbor.actor ! msg
        msg.origin ! SuperActor.Message(msg.nodesVisited.size, "relayed")
        log.info("Relayed search to neighbor: " + neighbor.actor.path)
        // Break out of iterator, since we have found a capable neighbor.
        return
      }
    }
  }

  private def deleteNeighbor(deleteNeighbor: GiaNode): Unit = {
    // Temporary variable for storing of removed elements for re-adding.
    val neighborsToKeep = new Array[GiaNode](neighbors.size)

    // Temporary tracker variable to break out of iteration upon finding.
    var found = false
    var index = 0

    // Removes the neighbor from the priority queue through iteration.
    while (neighbors.nonEmpty && !found) {
      val currentNode = neighbors.dequeue()

      if (currentNode.equals(deleteNeighbor)) {
        found = true
      } else {
        neighborsToKeep.update(index, currentNode)
        index += 1
      }
    }

    //Re-add all dequeued neighbor
    for (i <- 1 to index) {
      neighbors.enqueue(neighborsToKeep(i - 1))
    }
    this.node.degree -= 1
    this.node.satisfaction -= 1.toFloat / this.node.capacity.toFloat
  }

  private def getNeighborNode(ref: ActorRef): GiaNode = {
    for (neighbor <- this.neighbors) {
      if (neighbor.actor.equals(ref)) {
        return neighbor
      }
    }
    return null
  }

  private def addFile(file: GiaFile): Unit = {
    this.localContent += (file.name -> file)
  }

  private def deleteFile(file: GiaFile): Unit = {
    this.localContent -= file.name
  }

  private def replicate(neighborContent: List[GiaFile], neighbor: GiaNode): Unit = {
    // Give a full replication, we overwrite the content of the neighbor
    // with the payload of the message

    // Remove all (old) files of neighbor
    oneHopReplicationMap.removeContent(neighbor)
    // Add new list of files of neighbor
    oneHopReplicationMap.addContent(neighborContent, neighbor)
    log.debug("File replication received from:" + neighbor.actor.path)
  }

  private def addNeighborCheck(newNeighbor: GiaNode): Boolean = {
    if (this.node.satisfaction < 1) {
      // Case it does not need to drop a neighbor and always accepts.
      addNeighbor(newNeighbor)
    } else {
      val neighborsToDrop = this.neighbors.filter(neighbor => neighbor.capacity <= newNeighbor.capacity)
      var maxNode: GiaNode = null
      var maxNodeDegree = -1
      for (neighbor <- neighborsToDrop) {
        if (neighbor.degree > maxNodeDegree) {
          maxNode = neighbor
          maxNodeDegree = maxNode.degree
        }
      }
      if (maxNodeDegree > newNeighbor.degree) {
        maxNode.actor ! DeleteNeighbor(this.node)
        deleteNeighbor(maxNode)
        addNeighbor(newNeighbor)
      } else {
        false
      }
    }
  }

  private def addNeighbor(newNeighbor: GiaNode): Boolean = {
    newNeighbor.tokens += 100
    neighbors.enqueue(newNeighbor)
    this.node.satisfaction += 1.toFloat/this.node.capacity.toFloat
    this.node.degree += 1
    log.info("Neighbor added: " + sender.path)
    true
  }

  private def handleConnectionResponse(neighbors: mutable.PriorityQueue[GiaNode]) = {
    val max_nr_neighbors = this.node.capacity - this.neighbors.length
    var nr_neighbors_asked = 0
    while (nr_neighbors_asked < max_nr_neighbors && !neighbors.isEmpty && neighbors.head.capacity > this.node.capacity) {
      val newNeighbor = neighbors.dequeue()
      newNeighbor.actor ! AddNeighborAsk(this.node)
      nr_neighbors_asked += 1
    }
    val listBufferNeighbors = new ListBuffer[GiaNode]
    neighbors.copyToBuffer(listBufferNeighbors)
    while (nr_neighbors_asked < max_nr_neighbors && !listBufferNeighbors.isEmpty) {
      val random = Random.nextInt(listBufferNeighbors.size)
      val newNeighbor = listBufferNeighbors.remove(random)
      newNeighbor.actor ! AddNeighborAsk(this.node)
      nr_neighbors_asked += 1
    }
    log.info("ConnectionResponse received from : " + sender.path + "\n" +
      "Number of neighbors: " + neighbors.size)
  }

  private def updateSatisfaction(neighbor: ActorRef): Unit = {
    if (this.node.satisfaction > 0.8) {
      return
    } else if (this.node.satisfaction > 0.2) {
      neighbor ! AddNeighborAsk(this.node)
    } else {
      neighbor ! ConnectionRequest
      log.warning("Rewire big list")
    }
  }
}

object SimActor {

  def props(ip: String, port: Int, capacity: Int, superActor: ActorRef): Props = Props(new SimActor(ip, port, capacity, superActor))

  final case class Search(keyword: String, @volatile var maxNodesVisited: Int, nodesVisited: mutable.HashSet[ActorRef], origin: ActorRef)

  final case class Replicate(neighborContent: List[GiaFile])

  final case class ConnectionResponse(neighbors: mutable.PriorityQueue[GiaNode])

  final case class AddFile(newFile: GiaFile)

  final case class DeleteFile(deleteFile: GiaFile)

  final case class AddNeighborAsk(node: GiaNode)

  final case class AddNeighborConfirm(node: GiaNode)

  final case class DeleteNeighbor(deleteNeighbor: GiaNode)

  final case class Found(foundFiles: Option[GiaFile], actorRef: ActorRef)

  final case class ReceiveTokens(tokenCount: Int, newDegree: Int)

  final case class PrintNeighbors()

  final case class Report()

  case object ConnectionRequest

  private case object TriggerGrantToken

  private case object TriggerGrantTokenKey

  private case object TriggerReplicateToNeighbor

  private case object TriggerReplicateToNeighborKey

}
