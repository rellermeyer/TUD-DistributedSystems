package services

import dht.DHT
import logic.login.{LocatorInfo, State}


/**
 * Check if a list of peers are online at certain locations. If so return this information.
 */
object CheckIfOnlineWithLocation{
  /**
   * Try to find a peer with the given mail and location. If found run the call back with the path of that peer.
   */
  def apply(dht: DHT,hashedMail:String, locator: LocatorInfo, cb : (String) => Unit): Unit ={
    dht.getAll(hashedMail, {
      case Some(l : List[LocatorInfo]) => {
        val foundLocation = l.filter(e => e.state != State.offline)
          .find(e => e.locator == locator.locator)
        foundLocation match {
          case Some(e) => cb(e.path)
          case _ => ()
        }
      }
      case None => ()
    })
  }
}
