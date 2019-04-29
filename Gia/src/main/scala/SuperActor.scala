package gia.core

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Timers}

import scala.collection.mutable

class SuperActor(ip: String) extends Actor with ActorLogging with Timers {

  import SuperActor._

  private val r = scala.util.Random
  private val nodes = mutable.ListBuffer[GiaNode]()
  private var localCount = 0
  private var hopCount = 0
  private var relayCount = 0
  private var completeMessageCount = 0
  private var completeFilesFound = 0

  private var foundCount = 0
  private var completeFoundCount = 0

  private var successfulSearches = 0

  override def preStart(): Unit = super.preStart()

  override def receive: Receive = {

    case Message(nrOfPassedNeighbors: Int, t: String) => {
      if (t == "foundLocal") {
        if (nrOfPassedNeighbors < foundCount || foundCount == -1) {
          foundCount = nrOfPassedNeighbors
        }
        localCount += 1
      }
      if (t == "foundHop") {
        if (nrOfPassedNeighbors < foundCount || foundCount == -1) {
          foundCount = nrOfPassedNeighbors
        }
        hopCount += 1
      }
      if (t == "relayed") {
        relayCount += 1
      }
    }

    case AddNode(giaNode: GiaNode) => {
      log.info("Added " + giaNode.actor.path)
      nodes += giaNode
    }

    case Report() => {
      println(localCount + " " + hopCount + " " + relayCount)

      completeMessageCount += localCount + hopCount + relayCount
      completeFilesFound += localCount + hopCount

      if (localCount + hopCount > 0) {
        completeFoundCount += foundCount
        successfulSearches += 1
      }

      localCount = 0
      hopCount = 0
      relayCount = 0
      foundCount = -1
    }

    case ReportStatistics(nrOfQueries: Int, nrOfFiles: Int) => {
      println("Average number of message in the network per query: " + completeMessageCount.toFloat / nrOfQueries.toFloat)
      println("Average percentage of files found in the network: " +
        completeFilesFound.toFloat / nrOfQueries.toFloat / nrOfFiles.toFloat * 100 + "%" )
      println("Average number of messages until first file found (only if file is found): " + completeFoundCount.toFloat / successfulSearches.toFloat)
      println("Cases file is not found: " + (nrOfQueries - successfulSearches) + " which is " +
        (nrOfQueries - successfulSearches).toFloat / nrOfQueries.toFloat + "%")
    }

    case Search(file: String, origin: ActorRef) => {
      val node = nodes(r.nextInt(nodes.size))
      log.info("Sending search request for " + file + " to " + node.actor.path)
      search(node.actor, file, origin)
    }

  }

  private def search(actorRef: ActorRef, file: String, origin: ActorRef): Unit = {
    actorRef ! SimActor.Search(file, 32, new mutable.HashSet[ActorRef](), origin)
  }

}

object SuperActor {

  def props(ip: String): Props = Props(new SuperActor(ip))

  final case class AddNode(giaNode: GiaNode)

  final case class Message(nrOfPassedNeighbors: Int, t: String)

  final case class Report()

  final case class Search(file: String, origin: ActorRef)

  final case class ReportStatistics(nrOfQueries: Int, nrOfFiles: Int)

}