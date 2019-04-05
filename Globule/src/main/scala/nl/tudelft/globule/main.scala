package nl.tudelft.globule

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory


object Configs {
  val HOME_DIR = System.getProperty("user.home")
  val GLOBULE_DIR = HOME_DIR + "/.globule"
  val DATA_DIR = GLOBULE_DIR + "/data"

  private val root = ConfigFactory.load()
  val masterConf = root.getConfig("masterServer")
  val slaveConf = root.getConfig("slaveServer")

  val app = root.getConfig("application")

}

object masterServer extends App {

  import Configs._

  val servername = app.getString("servername")

  val system: ActorSystem = ActorSystem("globule", masterConf)

  val networkActor: ActorRef =
    system.actorOf(NetworkManager.props(system), "networkAgent")

  val ip = app.getString("publicip")
  val masterLocation = Location.lookupIpLocation(ip).get

  println("Master location = " + ip + " -> " + masterLocation)

  val webserverPort = masterConf.getString("webserver-port").toInt


  val requestManagerSelection = system.actorSelection("user/requestManager")
  val replicationManagerSelection = system.actorSelection("user/replicationManager")

  val masterRemote = NetworkManager.RemoteAddress(masterConf.getString("akka.remote.netty.tcp.hostname"), webserverPort.toString)

  val resourceManager: ActorRef = system.actorOf(Props(new ResourceManager(Some(replicationManagerSelection), masterRemote)))

  val negotiationActor: ActorRef = system.actorOf(Negotiator.props(masterLocation, masterRemote, servername, resourceManager), "negotiationAgent")

  val fileServer = new FileServer(masterLocation, resourceManager, masterRemote, true, servername)


  val replicationManager: ActorRef = system.actorOf(ReplicationManager.props(fileServer, negotiationActor, networkActor, requestManagerSelection, resourceManager), "replicationManager")

  val requestManager = system.actorOf(RequestManager.props(replicationManagerSelection, servername), "requestManager")

  WebServer.run(webserverPort, requestManager, "0.0.0.0")

}

object slaveServer extends App {

  import Configs._

  val servername = app.getString("servername")

  val system: ActorSystem = ActorSystem("globule", slaveConf)
  val webserverPort = slaveConf.getString("webserver-port").toInt

  val networkActor: ActorRef =
    system.actorOf(NetworkManager.props(system), "networkAgent")

  val slaveRemote = NetworkManager.RemoteAddress(slaveConf.getString("akka.remote.netty.tcp.hostname"), webserverPort.toString)


  val slaveLocation = Location.lookupIpLocation(app.getString("publicip")).get

  val resourceManager: ActorRef = system.actorOf(Props(new ResourceManager(None, slaveRemote)))
  val resourceManagerApi: ActorRef = system.actorOf(Props(new ResourceManagerApi(resourceManager)))

  val negotiationActor: ActorRef = system.actorOf(Negotiator.props(slaveLocation, slaveRemote, servername, resourceManagerApi), "negotiationAgent")

  val mirrorRequestManager = system.actorOf(Props(new MirrorRequestManager), "mirrorRequestManager")

  WebServer.run(webserverPort, mirrorRequestManager, "0.0.0.0")
}
