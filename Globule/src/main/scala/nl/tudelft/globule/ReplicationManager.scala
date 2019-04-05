package nl.tudelft.globule

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, Props}

import scala.collection.mutable


class FileRequest(val file: FileDescription, val clientIP: String, val latlong: Location)

class Bucket(val latlong: Location, var requests: List[FileRequest])

class AccessStore(var buckets: List[Bucket])

class FileServer(val latlong: Location,
                 val resourceManager: ActorRef,
                 val mirrorServerInfo: NetworkManager.RemoteAddress,
                 val master: Boolean = false,
                 val servername: String) {
  override def toString: String = "(" + latlong.toString + ", " + master + ", " + servername + ")"
}

class FileServerStore(val fileServer: FileServer,
                      var replicatedFiles: mutable.HashSet[String])


object ReplicationManager {

  // define messages to be received by Replication
  case class DocumentRequest(fileRequest: FileRequest)

  case class ReplicaNegotiated(fileServer: FileServer, resourceSpecification: Resources)

  case class FileReplicated(file: FileDescription, mirrorServer: NetworkManager.RemoteAddress)

  case class FileReplicaRemoved(file: FileDescription, mirrorServer: NetworkManager.RemoteAddress)

  case class CandidateReplicaList(latlong: Location, serverCandidates: List[ActorSelection])


  // constructor
  def props(mainFileServer: FileServer, negotiationActor: ActorRef, networkManager: ActorRef, requestManager: ActorSelection, resourceManager: ActorRef): Props =
    Props(new ReplicationManager(mainFileServer, negotiationActor, networkManager, requestManager, resourceManager))

  // in KM
  val BUCKET_RADIUS: Double = Configs.app.getInt("replication-bucket-radius")

  // in number of requests from this region
  val BUCKET_SPAWN_COUNT_THRESHOLD: Int = Configs.app.getInt("replication-request-threshold")

  // replication server radius
  val REPLICATION_SERVER_RADIUS: Double = BUCKET_RADIUS * 2

  def fileToString(file: FileDescription): String = {
    file.servername + ":" + file.filename
  }

  def fileRequestToString(request: FileRequest): String = {
    fileToString(request.file)
  }

  def deg2rad(deg: Double): Double = {
    deg * (Math.PI / 180)
  }

  def findClosestServer(serverList: List[FileServerStore], fileRequest: FileRequest, needsFile: Boolean = false): (FileServerStore, Double) = {

    var shortestDistance = Double.MaxValue
    var bestServer: FileServerStore = null

    for (fileServerStore <- serverList) {
      val dist = fileServerStore.fileServer.latlong.distanceTo(fileRequest.latlong)

      // the closest server that contains the replicated file
      if (dist < shortestDistance && (!needsFile || doesServerContainReplicatedFile(fileServerStore, fileRequest))) {
        bestServer = fileServerStore
        shortestDistance = dist
      }
    }

    println("findClosestServer: " + bestServer.fileServer + " " + shortestDistance)

    (bestServer, shortestDistance)
  }

  def doesServerContainReplicatedFile(fileServerStore: FileServerStore, fileRequest: FileRequest): Boolean = {
    // the main server always contains all files
    fileServerStore.fileServer.master || fileServerStore.replicatedFiles.contains(fileRequestToString(fileRequest))
  }
}

class ReplicationManager(mainFileServer: FileServer, negotiationManager: ActorRef, networkManager: ActorRef, requestManager: ActorSelection, resourceManager: ActorRef) extends Actor with ActorLogging {

  import NetworkManager._
  import ReplicationManager._

  // initialize object with replicationManager
  val fileAccessStore: mutable.HashMap[String, AccessStore] = new mutable.HashMap[String, AccessStore]()
  var serverList: List[FileServerStore] = List[FileServerStore](new FileServerStore(mainFileServer, new mutable.HashSet[String]()))

  // initialize with main server as server
  def serveRequest(fileRequest: FileRequest, sender: ActorRef): Unit = {

    val (closestFileServer, _) = findClosestServer(serverList, fileRequest, needsFile = true)
    sender ! RequestManager.ServeDocument(closestFileServer.fileServer, fileRequest)

  }

  def addReplica(server: FileServer): Unit = {
    serverList = new FileServerStore(server, new mutable.HashSet[String]()) :: serverList
  }

  def processAccessNotification(fileRequest: FileRequest): Unit = {
    // check if fileRequest is already in fileAccessStore otherwise create entry
    val fileRequestString = fileRequestToString(fileRequest)
    fileAccessStore.get(fileRequestString) match {
      case Some(i) => insertRequest(i, fileRequest)
      case None =>
        fileAccessStore.put(fileRequestString, new AccessStore(buckets = List()))
        insertRequest(fileAccessStore(fileRequestString), fileRequest)
    }
  }

  def insertRequest(accessStore: AccessStore, fileRequest: FileRequest): Unit = {

    // check for all buckets whether it is within store radius
    var foundBucket = false

    val bucketIterator = accessStore.buckets.iterator

    while (!foundBucket && bucketIterator.hasNext) {

      val bucket = bucketIterator.next()
      val dist = bucket.latlong.distanceTo(fileRequest.latlong)

      // if request falls in bucket, add it to bucket
      if (dist < BUCKET_RADIUS) {

        bucket.requests = fileRequest :: bucket.requests

        foundBucket = true

        // check whether bucket count is over threshold
        if (bucket.requests.length >= BUCKET_SPAWN_COUNT_THRESHOLD) {
          tryReplicatingDocument(fileRequest, bucket)
        } else {
          log.info("Bucket requests: " + bucket.requests.length)
        }
      }

    }

    // add bucket if was not in range of any bucket
    if (!foundBucket) {
      accessStore.buckets = new Bucket(latlong = fileRequest.latlong, requests = List()) :: accessStore.buckets
    }

  }

  def tryReplicatingDocument(fileRequest: FileRequest, bucket: Bucket): Unit = {

    // find closest server to replicate to
    val (closestFileServer, shortestDistance) = findClosestServer(serverList, fileRequest)

    /*

      This function does one of three things:
      1) when there is no replica server available within acceptable REPLICATION_SERVER_RADIUS
      it requests a replica at the bucket location

      2) when there is a server and it has already replicated the document nothing happens

      3) when there is a server close enough but the file is not yet replicated it requests the file to be replicated
      to the server's actor

      Hence, the insertion (insertRequest) both triggers requesting a replica server and  actually replicating a
      file to a nearby server

     */

    if (shortestDistance > REPLICATION_SERVER_RADIUS) {
      log.info("no server to replicate within radius")

      // no server to replicate to - can't replatced
      requestReplicationAtLocation(bucket.latlong)

    } else if (!doesServerContainReplicatedFile(closestFileServer, fileRequest)) {
      log.info("replicate to " + closestFileServer.fileServer.toString)

      // is not replicated but can be, replicate
      resourceManager ! TransferFileToRemote(fileRequest.file, closestFileServer.fileServer.resourceManager)
    } else {
      log.info("already replicated to " + closestFileServer.fileServer.toString)
    }

  }

  def processFileReplicated(file: FileDescription, remoteAddress: RemoteAddress): Unit = {
    for (fileServer <- serverList) {
      if (compareRemoteAddress(fileServer.fileServer.mirrorServerInfo, remoteAddress)) {
        fileServer.replicatedFiles.add(fileToString(file))
      }
    }
  }

  def processFileReplicateRemoved(file: FileDescription, remoteAddress: RemoteAddress): Unit = {
    for (fileServer <- serverList) {
      if (compareRemoteAddress(fileServer.fileServer.mirrorServerInfo, remoteAddress) && fileServer.replicatedFiles.contains(fileToString(file))) {
        fileServer.replicatedFiles.remove(fileToString(file))
      }
    }
  }

  def requestReplicationAtLocation(latlong: Location): Unit = {
    networkManager ! requestReplicas(latlong, BUCKET_RADIUS)
  }

  override def receive: Receive = {
    case DocumentRequest(fileRequest) =>
      processAccessNotification(fileRequest)
      serveRequest(fileRequest, sender)

    case ReplicaNegotiated(fileServer, _) =>
      addReplica(fileServer)

    case FileReplicated(file: FileDescription, remoteAddress: RemoteAddress) =>
      processFileReplicated(file, remoteAddress)

    case FileReplicaRemoved(file: FileDescription, remoteAddress: RemoteAddress) =>
      processFileReplicateRemoved(file, remoteAddress)

    case CandidateReplicaList(_, serverCandidates) =>
      negotiationManager ! NegotiateWithTargets(serverCandidates, Resources(100, 100 * 100, 3600 * 24))
  }
}
