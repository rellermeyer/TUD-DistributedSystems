package logic.login

import logic.login.State.State

case class LocatorInfo(
                        locator: String, // a string defined by user, to tell on which machine the user is currently active
                        IP: String,
                        port: String,
                        state: State, // only one peerID can be active at a time
                        path: String // path accessible by scala actors
                        // meshID: String, // optional, if a user is participating in a user mesh project
                        // GPS: String, // optional, coordinate based P2p neighbour selection
                        // timestamp: Int // time in seconds since 01.01.1970, used fot OpenDHT problems -> maybe not needed
                      )

object State extends Enumeration {
  type State = Value
  val online, active, offline = Value
}
