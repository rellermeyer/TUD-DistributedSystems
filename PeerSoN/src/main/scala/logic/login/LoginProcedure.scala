package logic.login

import dht.DistributedDHT

import java.io.{BufferedReader, InputStreamReader}
import java.net.URL

class LoginProcedure(val location: String, val hashedMail: String, val path: String, val DistributedDHT: DistributedDHT, val startingTimestamp: Long) {

  def start(): Unit = {
    DistributedDHT.contains(hashedMail, recievedContains)
  }

  def recievedContains(contains: Boolean): Unit ={
    if (contains) {
      login()
    } else {
      register()
    }
  }

  /**
   *
   * @param location location in string, say "laptop", "home"
   * @param hashedMail hashedMail
   */
  def login(): Unit = {
    // 1. get user info from the DHT
    DistributedDHT.getAll(hashedMail, receivedUserInfo)
  }

  def receivedUserInfo(userLocatorInfos: Option[List[Any]]): Unit ={
    var locationInfoList: List[LocatorInfo] = userLocatorInfos match {
      case Some(value) => value.asInstanceOf[List[LocatorInfo]]
      case None => throw new Exception()  // TODO: handle error
    }

    // 2. if no desired location add it
    val desiredLocation = locationInfoList.filter(l => l.locator == location)
    if (desiredLocation.isEmpty) {
      locationInfoList = LocatorInfo(location, findIPAddress(), "80", State.active, path) :: locationInfoList
    }

    // 3. update user info
    //  - only one location is active
    //  - but there might be multiple locations that are online
    //  - needs improvement if needed
    val updateUserInfo = locationInfoList.map(l => {
      val newState = {
        if (l.locator == location) State.active
        else {
          // active/online -> online
          if (l.state != State.offline) State.online
          else State.offline
        }
      }
      LocatorInfo(l.locator, l.IP, l.port, newState, l.path)
    })

    // 4. send new info to DHT
    //    LocalDHT.put(hashedMail, updateUserInfo.head)
    //    updateUserInfo.tail.foreach(l => LocalDHT.append(hashedMail, l))
    DistributedDHT.put(hashedMail, updateUserInfo)
    println("Current user data:")
    println(updateUserInfo)
    println("Time elapsed: " + (System.currentTimeMillis() - startingTimestamp)/1000.0)
  }

  def register(): Unit = {
    val ip = findIPAddress()
    val port = "80"
    val locatorInfo = LocatorInfo(location, ip, port, State.active, path)
    DistributedDHT.put(hashedMail, locatorInfo :: Nil)
    println("Current user data:")
    println(locatorInfo::Nil)
    println("Time elapsed: " + (System.currentTimeMillis() - startingTimestamp)/1000.0)
  }

  def findIPAddress(): String = {
    val whereIsMyIPURL = new URL("http://checkip.amazonaws.com")
    val in: BufferedReader = new BufferedReader(new InputStreamReader(whereIsMyIPURL.openStream()))
    in.readLine()
  }
}
