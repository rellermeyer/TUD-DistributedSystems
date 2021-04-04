package gia.core

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}


import scala.collection.mutable

class GnutellaActor(ip: String, port: Int, maxNeighbors: Int, superActor: ActorRef) extends Actor with ActorLogging {

  import GnutellaActor._

  private val localContent = mutable.HashMap[String, GiaFile]()
  private val node = new GiaNode(self, 0, 0, 0, 0)
  val neighbors = mutable.ListBuffer[GiaNode]()

  override def preStart(): Unit = {
    super.preStart()

    if (ip == "first") {
      log.info("Initial node started.")
    }
    else {
      // Initialization to connect to existing node
      val firstNeighborConnectionString = "akka.tcp://gia@" + ip + ":" + port + "/user/gnutellaActor"
      val firstNeighbor: ActorSelection = context.actorSelection(firstNeighborConnectionString)
      firstNeighbor ! ConnectionRequest
      log.info("Sent initial connection reqeust to " + firstNeighborConnectionString)
    }
    superActor ! SuperActorGnutella.AddNode(this.node)
  }

  override def postStop(): Unit = {
    super.postStop()
    // Tell all neighbors to purge your files and disconnect afterwards
    neighbors.foreach(n => {
      n.actor ! DeleteNeighbor(this.node)
    })
  }

  override def receive: Receive = {

    case msg@Search(_, _, _, _) => {
      searchFile(msg)
    }

    case Found(_) => {
    }

    case ConnectionRequest => {
      val sendNeighbors = neighbors.clone()
      sendNeighbors += this.node
      sender ! ConnectionResponse(sendNeighbors)
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

    case AddNeighborAsk(newNeighbor: GiaNode) => {
      if (neighbors.size < maxNeighbors) {
        neighbors += newNeighbor
        sender ! AddNeighborConfirm(this.node)
      }
    }

    case AddNeighborConfirm(newNeighbor: GiaNode) => {
      if (neighbors.size < maxNeighbors) {
        neighbors += newNeighbor
      }
    }

    case DeleteNeighbor(neighbor) => {
      deleteNeighbor(neighbor)
    }
  }

  private def searchFile(msg : Search) : Unit = {

    if (msg.maxNodesVisited <= msg.nodesVisited.size) {
      return
    }

    msg.nodesVisited.add(this.node.actor)

    if(localContent.contains(msg.keyword)) {
      msg.origin ! Found(localContent.get(msg.keyword))
      msg.origin ! SuperActorGnutella.Message(msg.nodesVisited.size, "foundLocal")
      log.info("File found locally: " + msg.keyword)
    }

    //Flooding the network here
    for(neighbor <- neighbors) {
      if (!msg.nodesVisited.contains(neighbor.actor)) {
        msg.nodesVisited += neighbor.actor
        neighbor.actor ! msg
        msg.origin ! SuperActorGnutella.Message(msg.nodesVisited.size, "relayed")
        log.info("File found via one-hop replication: " + msg.keyword)
      }
    }
  }

  private def deleteNeighbor(deleteNeighbor: GiaNode): Unit = {
    neighbors -= deleteNeighbor
  }

  private def addFile(file: GiaFile): Unit = {
    this.localContent += (file.name -> file)
  }

  private def deleteFile(file: GiaFile): Unit = {
    this.localContent -= file.name
  }

  private def handleConnectionResponse(neighbors: mutable.ListBuffer[GiaNode]) = {
    for(neighbor <- neighbors) {
      neighbor.actor ! AddNeighborAsk(this.node)
    }
  }
}

object GnutellaActor {

  def props(ip: String, port: Int, maxNeighbors: Int, superActor: ActorRef): Props = Props(new GnutellaActor(ip, port, maxNeighbors, superActor))

  final case class Search(keyword: String, @volatile var maxNodesVisited: Int, nodesVisited: mutable.HashSet[ActorRef], origin: ActorRef)

  final case class ConnectionResponse(neighbors: mutable.ListBuffer[GiaNode])

  final case class AddFile(newFile: GiaFile)

  final case class DeleteFile(deleteFile: GiaFile)

  final case class AddNeighborAsk(node: GiaNode)

  final case class AddNeighborConfirm(node: GiaNode)

  final case class DeleteNeighbor(deleteNeighbor: GiaNode)

  final case class Found(foundFiles: Option[GiaFile])

  case object ConnectionRequest

}

