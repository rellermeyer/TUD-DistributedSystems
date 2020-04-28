package org.orleans.silo.test

import com.typesafe.scalalogging.LazyLogging
import org.orleans.silo.dispatcher.Sender
import org.orleans.silo.services.grain.Grain

class GreeterGrain(_id: String) extends Grain(_id) with LazyLogging {

  logger.info("Greeter implementation running")

  /**
    *Receive method of the grain
    * @return
    */
  def receive = {
    case ("hi", _) =>
//      Thread.sleep(5000)
      logger.info("Hello back to you")
    case ("hello", sender: Sender) =>
//      Thread.sleep(5000)
      logger.info("Replying to the sender!")
      // Answer to the sender of the message
      // Asynchronous response
      sender ! "Hello World!"
    case (msg: String, _) =>
//      Thread.sleep(5000)
      Thread.sleep(25)
      logger.info(s"Received message $msg")
  }
}
