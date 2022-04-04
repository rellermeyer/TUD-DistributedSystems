package logic.login

import dht.DistributedDHT

class LogoutProcedure(val location: String, val hashedMail: String, val DistributedDHT: DistributedDHT, val startingTimestamp: Long) {

  def start(): Unit = {
    DistributedDHT.getAll(hashedMail, onReceivedLookup)
  }

  def onReceivedLookup(lookup:Option[List[Any]]): Unit ={
    lookup match {
      case Some(value) =>
        val locatorInfoList: List[LocatorInfo] = value.asInstanceOf[List[LocatorInfo]]

        val updateUserInfo = locatorInfoList.map(l => {
          val newState = {
            if (l.locator == location) State.offline
            else {
              l.state
            }
          }
          LocatorInfo(l.locator, l.IP, l.port, newState, l.path)
        })
        DistributedDHT.put(hashedMail, updateUserInfo)
        println("Current user data:")
        println(updateUserInfo)
        println("Time elapsed: " + (System.currentTimeMillis() - startingTimestamp)/1000.0)

      case _ => println(s"user not found by DHT!")
    }
  }
}
