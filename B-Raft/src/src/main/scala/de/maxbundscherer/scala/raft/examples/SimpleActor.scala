package de.maxbundscherer.scala.raft.examples

import akka.actor.{Actor, ActorLogging}

class SimpleActor extends Actor with ActorLogging {

  override def receive: Receive = {

    case data: String =>

      sender ! data + "-pong"

    case any: Any =>

      log.error(s"Got unhandled message '$any'")

  }

}