package org.tudelft.crdtgraph

import akka.actor.ActorSystem
import akka.cluster.MemberStatus
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
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

  def getOtherMembers(mainSystem: ActorSystem): String = {
    implicit val cluster = Cluster(mainSystem)
    var message = ""
    cluster.state.getMembers.forEach(message += _.address.toString + "\n")
    cluster.state.members.filter(_.status == MemberStatus.Up).foreach(message += _.address.toString + "\n")
    message
  }
}
