package de.maxbundscherer.scala.raft.examples

import akka.actor.{Actor, ActorLogging}

object SimpleFSMActor {

  //Initialize message/command
  case class Initialize(state: Int)

}

class SimpleFSMActor extends Actor with ActorLogging {

  import SimpleFSMActor._

  //Actor mutable state
  private var state = -1

  //Initialized behavior
  def initialized: Receive = {

    case any: Any => log.info(s"Got message '$any'")

  }

  //Default behavior
  override def receive: Receive = {

    case Initialize(newState) =>

      state = newState
      context.become(initialized)

    case any: Any => log.error(s"Not initialized '$any'")

  }

}