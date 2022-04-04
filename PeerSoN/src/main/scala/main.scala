import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.simtechdata.waifupnp.UPnP
import dht.{DHT, DistributedDHT}
import peer.{AddToWallCommand, GetFileCommand, PeerCmd, PeerMessage, SendMessageCommand}

import scala.collection.mutable
import scala.io.StdIn.readLine
import com.typesafe.config.ConfigFactory
import services.GetPeerKey

object Guardian {

  def apply(): Behavior[REPLCommand] = Behaviors.setup { context =>
    /**
     * create a map of peers, [a local backup for Guardian to speed up lookup]
     * s"'${user}'@${location}" -> ActorRef[PeerMessage]
     * only stores active/online users, offline users will be removed
     */
    val peers: mutable.Map[String, ActorRef[PeerMessage]] = mutable.Map()

    var counter = 2
    var dht : DHT = null // for wall add
    /**
     * get ActorRef if the message sender is now active/online
     * @param sender sender mail (not hashed)
     * @return ActorRef if exists, else None
     */
    def getPeerRefByGuardian(sender: String): Option[ActorRef[PeerMessage]] = {
      val validSenders = peers.keys.filter(k => k.startsWith(s"'$sender'"))
      if (validSenders.isEmpty) {
        None
      } else {
        Some(peers(validSenders.head))
      }
    }

    Behaviors.receiveMessage { msg: REPLCommand =>
      msg match {

        case Login(user: String, location: String) =>

          // check if this user is already logged in
          val peerKey = GetPeerKey(user, location)
          if (peers.contains(peerKey)){
            println("User is already logged in with this device")
          }

          else {

            val bootstrapHost = System.getenv("BOOTSTRAP")
            if(bootstrapHost == null){
              context.log.info("BOOTSTRAP not set")
            }else{
              context.log.info(s"BOOTSTRAP node is : ${bootstrapHost}")
            }

            // create a dht node for new peer
            val dhtNode: DistributedDHT = new DistributedDHT(counter,bootstrapHost)
            dht = dhtNode
            counter = counter + 1
            // create a new peer
            val peerRef = context.spawn(peer.Peer(user, dhtNode), peerKey)
            peers.put(peerKey, peerRef)
            // send a Login command to the peer
            val peerPath = peerRef.path.toStringWithAddress(context.system.address)
            peerRef ! peer.Login(location, peerPath)
          }

        case Logout(user: String, location: String) =>
          val lookup = getPeerRefByGuardian(user)
          lookup match {
            case Some(userRef: ActorRef[PeerMessage]) =>
              userRef ! peer.Logout(location)
              context.stop(userRef)
              peers.remove(GetPeerKey(user, location))
              println("Logout successful")
            case _ =>
              println(s"User $user currently unavailable")
          }

        /**
         * 1. if any location of receiver is found active/online, send message
         * 2. if not, add to wall
         */
        case SendMessage(sender: String, receiver: String, text: String) =>
          val lookup = getPeerRefByGuardian(sender)
          lookup match {
            case Some(senderRef: ActorRef[PeerMessage]) =>
              senderRef ! PeerCmd(SendMessageCommand(receiver, text))
            case _ =>
              println(s"Sender $sender currently unavailable")
          }

        case AddWallByUser(sender: String, owner: String, text: String) =>
          val lookup = getPeerRefByGuardian(sender)
          lookup match {
            case Some(senderRef: ActorRef[PeerMessage]) =>
              senderRef ! PeerCmd(AddToWallCommand(owner,text))
            case _ =>
              println(s"Owner $owner currently unavailable")
          }

        case RequestFileByUser(requester: String, responder: String, fileName: String, version: Int) =>
          val lookup = getPeerRefByGuardian(requester)
          lookup match {
            case Some(requesterRef: ActorRef[PeerMessage]) =>
              requesterRef ! PeerCmd(GetFileCommand(fileName,null))
            case _ =>
              println(s"Peer $requester currently unavailable")
          }

        case _ => ()
      }
      Behaviors.same
    }
  }
}



object main extends App {

  var isRemote = false
  if(args.length > 0){
    isRemote = true
  }

  UPnP.openPortTCP(6122)
  UPnP.openPortUDP(6122)
  UPnP.openPortTCP(5001)
  UPnP.openPortUDP(5000)
  UPnP.openPortTCP(5000)

  println(UPnP.getExternalIP)
  val guardian = setupGuardian(isRemote)


  while (true) {
    println("Guardian waits for your command")
    val input = readLine.strip.split(" ")
    val command: REPLCommand = input.head match {
      case "login" =>
        Login(readLine("email: ").strip, readLine("location: ").strip)
      case "logout" =>
        Logout(readLine("email: ").strip, readLine("location: ").strip)
      case "send-message" =>
        SendMessage(readLine("sender: ").strip, readLine("receiver: ").strip, readLine("text: ").strip)
      case "add-to-wall" =>
        AddWallByUser(readLine("sender: ").strip, readLine("owner: ").strip,  readLine("text: ").strip)
      case "request-wall" =>
        RequestFileByUser(readLine("requester: ").strip, ""/*readLine("responder: ").strip*/,
          readLine("fileName: ").strip, version = 0)
      case "exit" =>
        sys.exit
      case _ => new REPLCommand {}
    }
    guardian ! command
  }

  def setupGuardian(isRemote: Boolean): ActorSystem[REPLCommand] ={
    if (isRemote){
      val config = ConfigFactory.load("remote_application")
      ActorSystem(Guardian(), "guardian",config)
    } else {
      ActorSystem(Guardian(), "guardian")
    }
  }
}


