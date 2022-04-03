package services

import dht.DistributedDHT
import logic.login.{LocatorInfo, State}

class GetPathByMail(val mail: String, val distributedDHT: DistributedDHT, val callback: Option[String] => Unit) {

  /**
   * find a current active/online actor's path purely based on its mail
   * should be a function of DHT
   * @param mail mail to look up
   * @return path if exists, else None
   */
  def get(): Unit = {
    val hashedMail: String = Encrypt(mail)
    distributedDHT.getAll(hashedMail, onReceivedLookup)
  }

  def onReceivedLookup(lookup:Option[List[Any]]): Unit ={
    println(lookup)
    lookup match {
      case Some(value: List[LocatorInfo]) =>
        // filter an active or online locator info
        val validLocatorInfoList = value.filter(l => l.state == State.active || l.state == State.online)
        if (validLocatorInfoList.isEmpty) {
          println(s"peer $mail not found by DHT")
          callback(None)
        } else {
          callback(Some(validLocatorInfoList.head.path))
        }
      case _ =>
        callback(None)
    }
  }
}

