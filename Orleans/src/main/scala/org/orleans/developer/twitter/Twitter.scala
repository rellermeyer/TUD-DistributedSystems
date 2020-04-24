package org.orleans.developer.twitter
import org.orleans.developer.twitter.TwitterMessages._
import org.orleans.silo.dispatcher.Sender
import org.orleans.silo.services.grain.Grain
import org.orleans.silo.services.grain.Grain.Receive

class Twitter(id: String) extends Grain(id) {

  private var accounts: Map[String, String] = Map()

  override def receive: Receive = {
    case (user: UserExists, sender: Sender) => {
      if (accounts
            .get(user.username)
            .isEmpty) {
        sender ! TwitterSuccess()
      } else {
        sender ! TwitterFailure("User already exists.")
      }
    }
    case (user: UserCreate, sender: Sender) => //Add user here
      accounts = Map(user.username -> user.ref) ++ accounts
      sender ! TwitterSuccess()
    case (user: UserGet, sender: Sender) => {
      if (accounts.get(user.username).isDefined) {
        sender ! UserRetrieve(accounts.get(user.username).get)
      } else {
        sender ! TwitterFailure("Username doesn't exist.")
      }
    }
  }
}
