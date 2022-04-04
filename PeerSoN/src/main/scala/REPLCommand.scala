/**
 * a file for REPLCommand trait and child case classes
 */

trait REPLCommand

/**
 * REPL lets `user` to login
 * @param user user email (not hashed)
 * @param location location string
 */
case class Login(user: String, location: String) extends REPLCommand

/**
 * REPL lets `user` to logout
 * @param user user email (not hashed)
 * @param location location string
 */
case class Logout(user: String, location: String) extends REPLCommand

/**
 * REPL lets user with `mail` to send a message `test`
 * @param sender sender mail (not hashed)
 * @param receiver receiver mail (not hashed)
 * @param text text message
 */
case class SendMessage(sender: String, receiver: String, text: String) extends REPLCommand

/**
 * REPL lets `sender` to send a message `text` to `owner`'s wall
 * @param sender sender email (not hashed)
 * @param owner wall owner, message receiver, email (not hashed)
 * @param text message text
 */
case class AddWallByUser(sender: String, owner: String, text: String) extends REPLCommand

/**
 * REPL lets `requester` to request a file `fileName` of `version` from responder`
 * @param requester file requester, email (not hashed)
 * @param responder file responder, email (not hashed)
 * @param fileName filename
 * @param version version == 0 by default
 */
case class RequestFileByUser(requester: String, responder: String, fileName: String, version: Int) extends REPLCommand

