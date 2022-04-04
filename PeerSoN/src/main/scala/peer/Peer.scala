package peer

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import logic.async_messages.AsyncMessage
import logic.async_messages.AsyncMessage.OfflineMessage
import dht.DistributedDHT
import logic.wall.FileOperations.DHTFileEntry
import logic.login.{LocatorInfo, LoginProcedure, LogoutProcedure, State}
import logic.wall.Wall.WallEntry
import logic.wall.{File, FileOperations, Wall}
import services.{CheckIfOnlineWithLocation, Encrypt, GetPathByMail, GetPeerRef}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


object PeerWall {
  case class WallIndex(owner: String, lastIndex: Int, entries: ListBuffer[String]) extends File
}


object Peer {
  def apply(mail: String, dhtNode: DistributedDHT): Behavior[PeerMessage] = {
    Behaviors.setup(context => {
      new PeerBehavior(context, mail, dhtNode)
    })
  }

  class PeerBehavior(context: ActorContext[PeerMessage], mail: String, dhtNode: DistributedDHT) extends AbstractBehavior[PeerMessage](context) {

    private val hashedMail = Encrypt(mail)
    // the peer location, e.g., home / phone
    var location : String =""
    // the peer path, like akka://guadian@localhost/....
    var path : String =""

    /**
     * instance variable - a mutable map to store all the local files
     * TODO (if time allows): connects to JSON files or databases to fetch a peer's files
     * Now just create a new Map every time
     */
    val localFiles: mutable.Map[String, File] = mutable.Map()
    val WALL_INDEX_KEY = s"$hashedMail@wi"
    var wallIndex: PeerWall.WallIndex = PeerWall.WallIndex(hashedMail, -1, ListBuffer.empty)

    /**
     *
     * @param sender the hashed mail of the person who added the entry.
     */
    def addToWall(sender:String, text: String): Unit = {
      val newIndex = wallIndex.lastIndex + 1
      val entryKey = Wall.getWallEntryKey(mail,newIndex)
      wallIndex.entries.append(entryKey)
      localFiles.put(entryKey, WallEntry(newIndex,sender, text))                  // is this ok?
      dhtNode.append(entryKey, DHTFileEntry(hashedMail, LocatorInfo(location,"","",State.active,path), 0)) // for now assume not versioning

      // increment index
      localFiles.put(WALL_INDEX_KEY, wallIndex.copy(lastIndex = newIndex))
      
      dhtNode.append(WALL_INDEX_KEY, DHTFileEntry(hashedMail, LocatorInfo(location,"","",State.active,path), 0)) // for now assume not versioning
    }

    /**
     * Timestamps of when we sent a message
     */
    val timeStamps = mutable.Map[Long,Long]()
    // for keeping track of message timestamps
    var currentTimeStampId = 0L

    /**
     * message handler
     *
     * @param msg incoming Akka message
     */
    override def onMessage(msg: PeerMessage): Behavior[PeerMessage] = {
      context.log.info(s"$mail - received message: $msg")
      msg match {


        case AddWallEntry(sender,text) =>
          addToWall(sender,text)

        case Message(sender, text, ack,id) =>
          if (ack) {
            timeStamps.get(id) match {
              case Some(value) => {
                context.log.info(s"$sender sent an ack")
                context.log.info(s"Message sent and received ack with latency : ${(System.currentTimeMillis() - timeStamps(id))/1000.0}")
              }
              case _ => context.log.info("got ack for message we don't know about.")
            }

          } else {
            context.log.info(s"From: $sender | Message: $text")
            new GetPathByMail(sender, dhtNode,{
              case Some(senderPath: String) =>
                GetPeerRef(context, senderPath) ! Message(mail, "I got your message", ack = true,id)
              case _ =>
                AsyncMessage.add(mail, sender, "I got your message", ack = true, dhtNode)
            }).get()
          }


        case Login(location, path) =>
          AsyncMessage.load(context, mail, dhtNode)
          this.location = location
          this.path = path
          val loginProcedure = new LoginProcedure(location, hashedMail, path, dhtNode, System.currentTimeMillis())
          loginProcedure.start()


        case Logout(location) =>
          val logoutProcedure = new LogoutProcedure(location, hashedMail, dhtNode, System.currentTimeMillis())
          logoutProcedure.start()

        case FileRequest(fileName, version, replyTo,id) =>
          localFiles.get(fileName) match {
            case Some(value) if value.isInstanceOf[File] =>
              replyTo ! FileResponse(200, fileName, version, Some(value), context.self,id)
            case Some(_) => ()
            case None => replyTo ! FileResponse(404, fileName, version, None, context.self,id)
          }


        // If we get a response, store it locally.
        case FileResponse(code, fileName, version, received, from, id) =>
          timeStamps.get(id) match {
            case Some(value) =>  context.log.info(s"File response received in : ${(System.currentTimeMillis() - timeStamps(id))/1000.0}")
            case _ => context.log.info("got a response for request we don't know about")
          }
            if (code == 200){
              received match {
                case Some(file) =>
                  context.log.info("we received a response for our file request, storing and advertising the file...")
                  // store file locally and advertise it as well
                  localFiles.put(fileName, file)
                  dhtNode.append(fileName, DHTFileEntry(hashedMail, LocatorInfo(location,"","",State.active,path), 0))
                case None => ()
            }
          }


        case PeerCmd(cmd) =>
          cmd match {


            // command the current peer (as sender) to put text on receiver's wall
            case AddToWallCommand(receiver, text) =>
              new GetPathByMail(receiver, dhtNode,{
               case Some(receiverPath: String) =>
                 services.GetPeerRef(context, receiverPath) ! AddWallEntry(mail,text)
               case None => AsyncMessage.AddWallEntry(mail,receiver,text,dhtNode)
             }).get()


            case AddOfflineMessage(receiver, text, ack) =>
              AsyncMessage.add(mail, receiver, text, ack, dhtNode)


            // command the current peer to request a file
            case GetFileCommand(fileName, replyTo) =>
              var fileNameInDHT = fileName

              // wall index is hashed
              val extension = fileName.takeRight(3)
              if (extension == "@wi"){
                val realFileName = fileName.dropRight(3)
                fileNameInDHT = Encrypt(realFileName) + "@wi"
              }
              val id = currentTimeStampId
              currentTimeStampId +=1
              timeStamps.put(id,System.currentTimeMillis())


              dhtNode.getAll(fileNameInDHT, { case Some(l: List[DHTFileEntry]) =>
                var found = false
                for (e <- l) {
                  CheckIfOnlineWithLocation(dhtNode, e.hashedMail, e.locator, { foundPath =>
                    if (!found) {
                      found = true
                      GetPeerRef(context, foundPath) ! FileRequest(fileNameInDHT, 0, context.self,id)
                    }
                  })
                }
              case None => println(s"The file ${fileNameInDHT} could not be found.")
              })


            // command the current peer to send message
            case SendMessageCommand(receiver, text) =>
              new GetPathByMail(receiver,dhtNode,{
                case Some(receiverPath: String) =>
                  val id = currentTimeStampId
                  currentTimeStampId +=1
                  timeStamps.put(id,System.currentTimeMillis())
                  services.GetPeerRef(context, receiverPath) ! Message(mail, text, ack = false, id = id)
                case _ =>
                  context.self ! PeerCmd(AddOfflineMessage(receiver, text, ack = false))
              }).get()
            case _ => ()
          }


        case Notification(content) =>
          content match {


            case OfflineMessage(sender: String, content: String, ack: Boolean) =>
              context.self ! Message(sender, content, ack)


            case WallEntry(_,sender,content) => addToWall(sender,content)
          }
      }
      this
    }
  }
}