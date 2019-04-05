package nl.tudelft.globule

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Props, Terminated}

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.io.Source

object NetworkManager {

  def props(system: ActorSystem): Props = Props(new NetworkManager(system))

  final case class RemoteAddress(hostname: String, port: String) extends Serializable

  final case class message(message: String) extends Serializable

  final case class send(remote: RemoteAddress, message: message)

  final case class requestReplicas(location: Location, maxRadiusInKm: Double)

  // final case class hello(hostname: String, port: String, knownRemote: remote) extends Serializable
  final case class hello(hostname: String, port: String, knownRemotes: List[RemoteAddress]) extends Serializable

  def compareRemoteAddress(remote1: RemoteAddress, remote2: RemoteAddress): Boolean = {
    remote1.hostname.equals(remote2.hostname) && remote2.port.equals(remote2.port)
  }
}

class NetworkManager(system: ActorSystem) extends Actor with ActorLogging {

  import Location._
  import NetworkManager._
  import ReplicationManager._

  val localPort = system.settings.config.getString("akka.remote.netty.tcp.port")
  val localHostname = system.settings.config.getString("akka.remote.netty.tcp.hostname")

  var knownRemotesMap = scala.collection.mutable.ListBuffer[RemoteAddress]()
  var activeRemotesMap = scala.collection.mutable.ListBuffer[RemoteAddress]()

  override def preStart() {
    loadStaticNodes
    sayHelloToAll
  }

  def loadStaticNodes {
    val HOME_DIR = System.getProperty("user.home")
    val lines = Source.fromFile(HOME_DIR + "/.globule/peers.conf").getLines.toList
    for (ip <- lines) {
      if (!ip.isEmpty) {
        val masterPort = Configs.masterConf.getString("akka.remote.netty.tcp.port")
        val slavePort = Configs.slaveConf.getString("akka.remote.netty.tcp.port")
        val port = if (masterPort == localPort) slavePort else masterPort
        println(s"Adding ${ip}:${port} to list of known hosts")
        knownRemotesMap += RemoteAddress(ip, port)
      }
    }
  }

  def sayHelloToAll {
    for (remote <- knownRemotesMap) {
      sayHello(remote)
    }
  }

  def sayHello(remote: RemoteAddress) {
    if (remote.hostname != localHostname) {
      log.info("say hello to " + remote.hostname + ":" + remote.port)
      val remoteActor: ActorSelection =
        system.actorSelection("akka.tcp://globule@" + remote.hostname + ":" + remote.port + "/user/networkAgent")
      remoteActor ! hello(localHostname, localPort, knownRemotesMap.toList)
    }
  }

  def addActive(remoteKey: String, remoteSender: RemoteAddress, remoteActorRef: ActorRef) {
    context.watch(remoteActorRef)

    if (!activeRemotesMap.contains(remoteSender)) {
      log.info("got hello from {}", remoteKey)
      activeRemotesMap += remoteSender
    }
  }

  def removeActive(remoteActor: ActorRef) {
    activeRemotesMap -= actorRefToKeyMap(remoteActor)
  }

  def actorRefToKeyMap(remoteActor: ActorRef): RemoteAddress = {
    RemoteAddress(remoteActor.path.address.host.get, remoteActor.path.address.port.get.toString)
  }

  def receive = {

    case hello(remoteHost, remotePort, knownRemotes) =>
      val remoteKey = remoteHost + ":" + remotePort
      println("Received hello from: " + remoteKey)
      if (remotePort != localPort) {
        //check if remote host is not already known
        val remoteSender = RemoteAddress(remoteHost, remotePort)
        if (!knownRemotesMap.contains(remoteSender)) {
          // else say hello and add to the list of actives
          knownRemotesMap += remoteSender
          addActive(remoteKey, remoteSender, sender)
          println("ADDING " + remoteKey + "to known and active")
          sayHello(remoteSender)
        } else if (!activeRemotesMap.contains(remoteSender)) {
          addActive(remoteKey, remoteSender, sender)
          println("ADDING " + remoteKey + "to active")
          sayHello(remoteSender)
        }

        //check for each entry if the remotesList if they are known
        for (knownRemote <- knownRemotes) {
          if (!knownRemotesMap.contains(knownRemote)) {
            // else say hello and add to the list of actives
            knownRemotesMap += knownRemote
            sayHello(knownRemote)
          }
        }
      } else {
        println("IGNORING " + remoteKey + " with matching port")
      }


    case send(remote, message) =>
      println(s"Sending message to ${remote.hostname}:${remote.port}")
      val remoteActor: ActorSelection =
        system.actorSelection("akka.tcp://globule@" + remote.hostname + ":" + remote.port + "/user/networkAgent")
      remoteActor ! message


    case requestReplicas(location, maxRadiusInKm) =>

      log.info("requestReplicas at " + location + " within " + maxRadiusInKm)
      log.info("activeRemotes: " + activeRemotesMap)

      var remotes = new ListBuffer[RemoteAddress]()

      for (remote <- activeRemotesMap) {
        val ipaddress = InetAddress.getByName(remote.hostname)
        if (localHostname == remote.hostname && localPort == remote.port) {
          //own address
        } else if (ipaddress.isSiteLocalAddress()) {
          //private address
          remotes += remote
        } else if (ipaddress.isLoopbackAddress()) {
          //loopback address
          remotes += remote
        } else {
          //public address
          val serverLoc = lookupIpLocation(remote.hostname)
          log.info("lookupIpLocation " + remote.hostname + " -> " + serverLoc)
          serverLoc match {
            case Some(loc) =>
              if (loc.distanceTo(location) <= maxRadiusInKm) {
                //within range of target
                println(loc)
                remotes += remote
              }
            case _ =>
              None
          }
        }
      }
      //resolve serverCandidates
      var serverCandidates = new ListBuffer[ActorSelection]()
      for (remote <- remotes) {
        val remoteActor: ActorSelection =
          system.actorSelection("akka.tcp://globule@" + remote.hostname + ":" + remote.port + "/user/negotiationAgent")
        serverCandidates += remoteActor
      }
      log.info("found candidates: " + serverCandidates.toList)
      sender ! CandidateReplicaList(location, serverCandidates.toList)


    case message(text) =>
      println(text)

    case Terminated(actor: ActorRef) =>
      removeActive(actor)
  }

}
