package core

import java.io._
import java.net.{InetAddress, ServerSocket, Socket, SocketException}
import java.util.concurrent.{ScheduledThreadPoolExecutor, TimeUnit}

import clock.Clock
import controller.{BodyRequestScheduler, ReqFile, ResLocation}
import invalidationlog._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.Random

case class Location(virtualNode: VirtualNode, timestamp: Int) {
}

case class Controller(node: Node) extends Thread("ControlThread") {


  var log = new Log(node.logDir + "log.txt")
  val processors = List[Processor](new InvalidationProcessor(this), new CheckpointProcessor(this))
  val processedRequests = new ListBuffer[Int]()
  val processedResponses = new ListBuffer[Int]()

  /**
    * Table of (object id, peer) describing which peer should be asked next if looking for corresponding object
    */
  val locationTable = new mutable.HashMap[String, mutable.Queue[VirtualNode]]() //map(object id, peer)
  private val acceptSocket = new ServerSocket(node.getControllerPort, 50, InetAddress.getByName(node.hostname))
  this.start()
  this.checkInvalidationsScheduled()

  // Left this method in order to rollback if something doesnt work.
  // you should use invalidationProcessor class and method "processUpdate"
  @Deprecated
  def invalidate(objectId: String, newStamp: Boolean = true): Unit = {
    val invalidation = Invalidation(objectId, node.clock.time, node.id)

    sendInvalidationForAllNeighbours(invalidation, newStamp)
  }

  /**
    * Check if there are any bodies invalid every x seconds.
    */
  def checkInvalidationsScheduled(): Unit = {
    val ex = new ScheduledThreadPoolExecutor(1)
    val task = new BodyRequestScheduler(node)
    ex.scheduleAtFixedRate(task, 3, 3, TimeUnit.SECONDS)
  }

  def requestBody(objectId: String): Unit = {
    val currentVirtualNode = node.getVirtualNode()
    val requestId = new Random().nextInt()
    if (locationTable.contains(objectId)) {
      //It knows which neighbour to ask for the file
      val frontNode = locationTable(objectId).front
      val fileRequest = ReqFile(currentVirtualNode, currentVirtualNode, objectId, frontNode,
        List(frontNode, currentVirtualNode), requestId)
      processedRequests += requestId
      fileRequest.send()
    } else {
      //It does not know which neighbour to ask; broadcast to all neighbours
      for (n <- node.neighbours) {
        val fileRequest = ReqFile(currentVirtualNode, currentVirtualNode, objectId,
          n, List(n, currentVirtualNode), requestId)
        processedRequests += requestId
        fileRequest.send()
      }
    }
  }

  def connectToNodeController(virtualNode: VirtualNode): Unit = {
  }

  /**
    * Controller method that is responsible for sending invalidations for all neighbors found in @node
    *
    * @param invalidation
    */
  def sendInvalidationForAllNeighbours(invalidation: Invalidation, newStamp: Boolean = true): Unit = {
    // stamp the operation
    if (newStamp) node.clock.sendStamp(invalidation)
    node.neighbours.foreach(n => n.sendToControllerAsync(invalidation))
  }

  override def run(): Unit = {
    while (true)
      InvalidationThread(acceptSocket.accept()).start()
  }

  /**
    * Class that handles invalidation stream. Each invalidation is handled in @InvalidationProcessor.
    *
    * @param socket socket on witch invalidations are expected to arrive.
    */
  case class InvalidationThread(socket: Socket) extends Thread("InvalidationThread") {

    override def run(): Unit = {
      node.logMessage("New connection: " + socket)
      // controllers need to be continuously connected to each other awaiting invalidations
      val ds = new DataInputStream(socket.getInputStream)
      // reset the Object input stream each time
      val in = new ObjectInputStream(ds)

      try {
        while (true) {
          // block until invalidations actually come
          val o = in.readObject()
          o match {
            case reqFile: ReqFile =>
              //If the request is already processed by this node do not do it again
              if (!processedRequests.contains(reqFile.requestId)) {
                processedRequests += reqFile.requestId

                //If the current node has the file but has not yet marked it in its table, do so now
                if (node.hasValidBody(reqFile.objectId)) {
                  node.logMessage(s"ObjectId ${reqFile.objectId} is present and valid.")
                  if (!locationTable.contains(reqFile.objectId)) {
                    val nodeQueue = mutable.Queue[VirtualNode]()
                    nodeQueue.enqueue(node.getVirtualNode())
                    locationTable.put(reqFile.objectId, nodeQueue)
                  }
                  val locationResponse = ResLocation(reqFile.objectId, reqFile.path, node.getVirtualNode(), reqFile.requestId)
                  locationResponse.send()
                } else {
                  node.logMessage(s"ObjectId ${reqFile.objectId} is not present/valid.")
                  //Current node does not have the file; ask neighbour(s)
                  if (locationTable.contains(reqFile.objectId)) {
                    val frontNode = locationTable(reqFile.objectId).front
                    //The node does not have the file but one of the neighbours may have it
                    val fileRequest = ReqFile(reqFile.originator, node.getVirtualNode(), reqFile.objectId,
                      frontNode, frontNode +: reqFile.path, reqFile.requestId)
                    fileRequest.send()
                  } else {
                    //Node does not know where the object is so ask all neighbours
                    for (n <- node.neighbours) {
                      val fileRequest = ReqFile(reqFile.originator, node.getVirtualNode(), reqFile.objectId, n,
                        n +: reqFile.path, reqFile.requestId)
                      fileRequest.send()
                    }
                  }
                }

              }
            case invalidation: Invalidation => {
              node.logMessage("Received invalidation with timestamp " + invalidation.timestamp + " for " + invalidation.objId + " from " + invalidation.nodeId)

              if (invalidation.nodeId != node.id) {
                if (locationTable.contains(invalidation.objId)) {
                  locationTable.remove(invalidation.objId)
                }

                node.logMessage("Invalidating")
                //process the invalidation
                processors.foreach(p => p.process(invalidation))
              }


            }
            case resLocation: ResLocation => {
              if (!processedResponses.contains(resLocation.requestId) && resLocation.originator.id != node.id) {
                processedResponses += resLocation.requestId
                //The node gets information about where to find a file
                //Never update the location, use the first location only as this is most likely the fastest path to use
                // for requests in the future
                if (!locationTable.contains(resLocation.objectId)) {
                  val nodeQueue = mutable.Queue[VirtualNode]()
                  nodeQueue.enqueue(node.getVirtualNode())
                  locationTable.put(resLocation.objectId, nodeQueue)
                } else if (!locationTable(resLocation.objectId).contains(node.getVirtualNode())) {
                  // theres a new location for a already known file
                  // so that location will be added to the queue
                  locationTable(resLocation.objectId).enqueue(node.getVirtualNode())
                }
                if (resLocation.path.tail.nonEmpty) {
                  val newResLocation = ResLocation(resLocation.objectId, resLocation.path.tail, resLocation.originator, resLocation.requestId)
                  newResLocation.send()
                } else {
                  //The node is the one originally asking for the body; so send an HTTP request to the correct location
                  // to actually obtain it


                  resLocation.sendBodyRequest(node)
                }
              }
            }
            case _ => println("This shouldn't happen")
          }
        }
      }
      catch {
        case e: SocketException =>
          e.printStackTrace()
        case e: IOException =>
          e.printStackTrace();
        case e: EOFException =>
          e.printStackTrace()
      }
      finally {
        // Do the cleanup
        ds.close()
        in.close()
        socket.close()
      }

    }

  }

}
