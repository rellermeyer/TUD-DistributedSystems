package org.tudelft.crdtgraph

import akka.actor.ActorSystem
import akka.cluster.{Member, MemberStatus}
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ArrayBuffer
//import akka.stream.ActorMaterializer
//import com.typesafe.config.ConfigFactory
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.cluster.Cluster
import akka.management.scaladsl.AkkaManagement

object ClusterListener {

  def startManager(mainSystem: ActorSystem): Unit = {
    AkkaManagement(mainSystem).start()
    ClusterBootstrap(mainSystem).start()
  }

  def getSelfAddress(mainSystem: ActorSystem): String = {
    implicit val cluster = Cluster(mainSystem)
    cluster.selfMember.address.toString
  }

  def getAvailableMembers(mainSystem: ActorSystem): String = {
    implicit val cluster = Cluster(mainSystem)
    var message = ""
    cluster.state.getMembers.forEach(message += _.address.toString + "\n")
    cluster.state.members.filter(_.status == MemberStatus.Up).foreach(message += _.address.toString + "\n")
    message
  }

  def getAllMembers(mainSystem: ActorSystem): String = {
    implicit val cluster = Cluster(mainSystem)
    var message = ""
    cluster.state.getMembers.forEach(message += _.address.toString + "\n")
    message
  }

  def getBroadcastAddresses(mainSystem: ActorSystem): ArrayBuffer[String] = {
    implicit val cluster = Cluster(mainSystem)
    // Get all available members
    var allAvailabeMembers = cluster.state.members.filter(_.status == MemberStatus.Up)
    // Remove self from set
    allAvailabeMembers -= cluster.selfMember
    // Create new string buffer
    val broadcastAddresses = ArrayBuffer[String]()
    allAvailabeMembers.foreach(broadcastAddresses += "http://" + _.address.host.getOrElse("") + ":8080")
    broadcastAddresses
  }

  def waitForUp(mainSystem: ActorSystem): Unit = {
    implicit val cluster = Cluster(mainSystem)
    while(cluster.state.members.filter(_.status == MemberStatus.Up).size < 5){
      Thread.sleep(1000)
    }
  }
}
