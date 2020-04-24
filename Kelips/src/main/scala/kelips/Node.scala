package kelips

import java.net.InetAddress
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akkaMessageCases.FileMessageCases.ReturnFileUpload
import akkaMessageCases.GossipMessageCases.Gossip
import akkaMessageCases.{FileMessageCases, GossipMessageCases, JoinMessageCases, UtilMessageCases}
import kelips.Utils.parseActor
import main.scala.kelips.{AVLTree, FileTupleOrdering}
import testing.TestMessageCases.{Close, WriteInsertion, WriteLookup, WriteText}
import testing.Writer

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
  * Main object
  */
object Main {

  /**
    * This class represents a node in the kelips system.
    * @param joinerIP The name of this node
    * @param numberOfNodes The number of nodes in the system
    */
  class Node(val joinerIP: String, val writerActor: ActorRef, numberOfNodes: Int, verbose: Boolean) extends Actor {
    val numberOfGroups: Int = Math.sqrt(numberOfNodes).ceil.toInt
    val localIpAddress: String = InetAddress.getLocalHost.getHostAddress + joinerIP
    val affinityGroupID: Int = Math.floorMod(Utils.hash(localIpAddress), numberOfGroups)
    // The fraction of contacts this node should gossip to on heartbeat
    val crossGroupGossipContactFraction = 0.8
    // This should be proportional to the system size
    val rttTargetChoosingRate = 0.8
    var heartBeat = 0
    var softState: SoftState = populateSoftState(joinerIP)
    println(joinerIP + " " + affinityGroupID)
    // Start gossiping in the background
    startGossipBackground()

    /****************Testing variables**********************/
    var timeBeforeLookup: mutable.HashMap[String, Long] = new mutable.HashMap[String, Long]()
    var timeBeforeInsertion: mutable.HashMap[String, Long] = new mutable.HashMap[String, Long]()

    /**
      * Starts periodically gossiping
      * @return
      */
    def startGossipBackground(): ScheduledFuture[_] = {
      val ex = new ScheduledThreadPoolExecutor(1)
      val gossipTask = new Runnable {
        def run() = {
          spreadGossip()
        }
      }
      ex.scheduleAtFixedRate(gossipTask, 1000, 1000 + (Math.random() * 1000).toLong, TimeUnit.MILLISECONDS)
    }


    /**
      * Populate initial softSTate
      * @param joinerIP
      * @return
      */
    def populateSoftState(joinerIP: String): SoftState = {
      val contactList: ListBuffer[Contact] = ListBuffer()
      for (i <- 0 until numberOfGroups) {
        if (i != affinityGroupID) {
          contactList += new Contact(i, new ListBuffer[ContactNode])
        }
      }
      new SoftState(new AffinityGroupView, contactList, new AVLTree[FileTuple]()(FileTupleOrdering))
    }

    /**
      * Determine the target group inside the affinity group where the logbook will be gossiped to
      * @return A listBuffer containing the target group entries
      */
    def determineAffinityGroupGossipTarget(): ListBuffer[GroupEntry] = {
      val result = ListBuffer[GroupEntry]()
      softState.affinityGroupView.groupEntries.foreach(groupEntry => {
        if (Math.random() <= rttTargetChoosingRate) {
          result += groupEntry
        }
      })
      result
    }

    /**
      * Determine the contact target group to which this node will gossip in the next heartbeat.
      */
    def determineContactGossipTarget(): ListBuffer[Contact] = {
      val result = ListBuffer[Contact]()
      softState.contacts.foreach(contact => {
        if (Math.random() <= crossGroupGossipContactFraction) {
          result += contact
        }
      })
      result
    }

    /**
      * Get a random Data item out of the list
      */
    def getRandomDataItem(dataList: ListBuffer[Data]): Data = {
      dataList((dataList.size * Math.random().doubleValue()).floor.toInt)
    }

    /**
      * Print information about this node's softstate
      */
    def printSoftState(): Unit = {
      println("---------------- SoftState ---------------------")
      softState.affinityGroupView.groupEntries.foreach(groupEntry => {
        println("(Group entry) " + parseActor(groupEntry.actorRef.toString()) + " / " + groupEntry.heartBeatCounter + " / " + groupEntry.heartBeatID)
      })
      softState.contacts.foreach(contact => {
        contact.contactNodes.foreach(contactNode => {
          println("(Contact) " + parseActor(contactNode.actorRef.toString()) + " / " + contactNode.heartBeatCounter + " / " + contactNode.heartBeatID)
        })
      })
      softState.fileTuples.foreach(fileTuple => {
        println("(File Tuple) " + fileTuple.fileName + " / " + fileTuple.heartBeatCounter + " / " + fileTuple.heartBeatID)
      })
    }

    /**
      * Print information about this node's logBook
      */
    def printLogBook(): Unit = {
      println("-------------------- LogBook ---------------------")
      softState.logBook.gossipItemList.foreach(x => {
        x.data match {
          case entry: GroupEntry => println(x.eventType + ": (GroupEntry) "
            + parseActor(entry.actorRef.toString()) + " / " + entry.heartBeatCounter)
          case contactNode: ContactNode => println(x.eventType + ": (Contact) "
            + parseActor(contactNode.actorRef.toString()) + " / " + contactNode.heartBeatCounter)
          case tuple: FileTuple => println(x.eventType + ": (FileTuple) "
            + tuple.fileName + " / " + x.data.heartBeatCounter)
          case _ =>
        }
      })
    }

    /**
      * Spread the log book to all target groups
      */
    def spreadGossip(): Unit = {
      if (softState.logBook.gossipItemList.nonEmpty && verbose) {
        println("******************************************************************************************")
        println(joinerIP + " is gossiping in round " + heartBeat + " with logbookSize: " + softState.logBook.gossipItemList.size)
        printSoftState()
        printLogBook()
      }
      softState.logBook.updateLog(new GroupEntry(self, heartBeat,1, heartBeat))
      // Gossip to a target group of this node's affinity group view
      val targetGroup = determineAffinityGroupGossipTarget()
      targetGroup.foreach(groupEntry => {
        groupEntry.actorRef ! Gossip(softState.logBook)
      })

      // Gossip to a target group of this node's contacts
      val targetContacts = determineContactGossipTarget()
      val affinityGroupEntries = determineAffinityGroupGossipTarget().clone()
      affinityGroupEntries += new GroupEntry(self, heartBeat, 1, heartBeat)
      targetContacts.foreach(contact => {
        contact.contactNodes.foreach(item => item.actorRef ! GossipMessageCases.GossipGroupEntriesAndContacts(affinityGroupID, affinityGroupEntries,
          softState.contacts.filter(onlyNonEmptyContact => onlyNonEmptyContact.contactNodes.nonEmpty)))
      })

      // Increment heartbeat and check for expiring data in the softstate
      heartBeat += 1
      softState.checkForExpiry(heartBeat, self)
    }

    /**
      * Add a ContactNode to the softstate. Initializing method
      */
    def addContactNode(contactNode: ContactNode): Unit = {
      softState.addContactNode(contactNode)
      softState.logBook.addLog(contactNode)
    }

    /**
      * Add a GroupEntry to the softstate. Initializing method
      */
    def addGroupEntry(groupEntry: GroupEntry): Unit = {
      softState.affinityGroupView.addGroupEntry(groupEntry)
      softState.logBook.addLog(groupEntry)
    }

    /**
      * Sanitize a received logBook, by removing the entries concerning itself.
      */
    def sanitizeLogBook(logBook: LogBook): LogBook = {
      val sanitizedLogBook = new LogBook(logBook.gossipItemList.clone())
      sanitizedLogBook.gossipItemList.foreach(item => {
        item.data match {
          case x: GroupEntry => if (x.actorRef.equals(self)) {
            sanitizedLogBook.gossipItemList -= item
          }
          case _ =>
        }
      })
      sanitizedLogBook
    }

    /**
      * Akka defined class for handling received messages
      */
    override def receive: Receive = {
      // Initializing message. Find out how to access internal object in actor
      case UtilMessageCases.AddContactNode(contactNode) => if (!softState.hasContactNode(contactNode)) this.addContactNode(contactNode)
      // Initializing message. Add group entry
      case UtilMessageCases.AddGroupEntry(groupEntry) => if(!softState.affinityGroupView.hasEntry(groupEntry)
        && !groupEntry.actorRef.equals(self))
        this.addGroupEntry(groupEntry)
      // Add file to system
      case FileMessageCases.InsertFiletupleCall(fileName, replicationFactor) =>
        timeBeforeInsertion.put(fileName, System.nanoTime())
        softState.insertFile(fileName, numberOfGroups, replicationFactor, self)
      // Lookup filetuple in system
      case FileMessageCases.LookupFiletupleCall(fileName) =>
        timeBeforeLookup.put(fileName, System.nanoTime())
        softState.lookupFile(fileName, numberOfGroups, self)

      // Initialize a join on a node
      case JoinMessageCases.InitJoin(welcomeNode) => welcomeNode ! JoinMessageCases.JoinRequest(affinityGroupID)
      // Send a join request to welcomeNode
      case JoinMessageCases.JoinRequest(groupId) =>
        sender ! JoinMessageCases.JoinAddress(softState.contacts(groupId).contactNodes.head.actorRef)
      // Send a contact from groupId to the joining node as warmUpNode
      case JoinMessageCases.JoinAddress(warmUpNode) => warmUpNode ! JoinMessageCases.RequestJoinView()
      // Request a joining view from the warmUp node
      case JoinMessageCases.RequestJoinView() => sender ! JoinMessageCases.SendJoinView(softState)
      // Send the softstate to the joining node as a joining view
      case JoinMessageCases.SendJoinView(state) => {
        softState = state
        println(joinerIP + " Welcome")
      }

      // Method called when a file needs to be inserted into
      // this affinityGroup. Distribute over affinityGroup.
      case FileMessageCases.InsertRequest(originator, fileName, replicationFactor) => {
        for (_ <- 0 until replicationFactor) {
          val actor: ActorRef = softState.affinityGroupView.getRandomGroupEntry(self)
          actor ! FileMessageCases.InsertFiletuple(originator, fileName)
        }
      }

      // Insert file into filetuples.
      // Inserting the real file is abstracted away from.
      case FileMessageCases.InsertFiletuple(originator, fileName) => {
        val fileTuple = new FileTuple(fileName, this.self, this.heartBeat, this.heartBeat)
        softState.fileTuples.insert(fileTuple)
        softState.logBook.addLog(fileTuple)
        originator ! ReturnFileUpload(fileName)
        println("[SUCCESS] Inserted file: " + fileName + " at node " + joinerIP + " in group " + affinityGroupID + ".")
      }

      // Handles file lookup request from a node.
      // Find the node that actually contains the
      // file in the filetuples.
      case FileMessageCases.RequestFile(originator, fileName) => {
        // Sender and heartBeatCounter are dummy values,
        // they are not needed for the lookup.
        softState.fileTuples.findNode(new FileTuple(fileName, originator, 0, -1)) match {
          case Some(i) => i.key.homeNode ! FileMessageCases.SendFile(originator, fileName)
          case _ => println("[ERROR] File " + fileName + " does not exist in the system.")
        }
      }

      // Node that contains actual requested filetuple,
      // handle the file request.
      case FileMessageCases.SendFile(originator, fileName) =>
        softState.fileTuples.findNode(new FileTuple(fileName, sender, 0, -1)) match {
          case Some(i) => {
            println("[SUCCESS] Found file " + fileName + " in affinity group " + affinityGroupID + " on node " + i.key.homeNode)
            i.key.heartBeatCounter = heartBeat
            i.key.heartBeatID = heartBeat
            softState.logBook.updateLog(i.key)
            originator ! FileMessageCases.ReturnFileLookup(fileName)
          }
          case _ => println("[ERROR] File " + fileName + " does not exist in the system.")
        }

      case ReturnFileUpload(fileName) =>
        writerActor ! WriteInsertion((((System.nanoTime() - timeBeforeInsertion(fileName)) / 1000).toDouble / 1000).toString)

      case FileMessageCases.ReturnFileLookup(fileName) =>
        writerActor ! WriteLookup((((System.nanoTime() - timeBeforeLookup(fileName)) / 1000).toDouble / 1000).toString)

      /**
        * Received gossip from group entry.
        */
      case Gossip(logBook) => {
        softState.update(sanitizeLogBook(logBook))
      }

      /**
        * Received gossip from other group with groupId
        */
      case GossipMessageCases.GossipGroupEntriesAndContacts(groupId, groupEntriesList, contacts) =>
        softState.updateFromContact(groupId, groupEntriesList, contacts)


      case _ => println("Unrecognized message received")
    }
  }

  /**
    * Initialize the system with a number of nodes.
    * @param numberOfNodes The number of nodes in the system.
    * @param numberOfKnownGroupEntries The number of known group entries beforehand. 2 is the minimum
    */
  def initSystem(numberOfNodes: Int, numberOfKnownGroupEntries: Int = 2, verbose: Boolean): Unit = {
    assert(numberOfKnownGroupEntries > 1)
    val system = ActorSystem("Kelips")
    val writer = system.actorOf(Props(new Writer("testResults", "normal.txt", "")))
    val numberOfGroups: Int = Math.sqrt(numberOfNodes).ceil.toInt
    var nodeList: ListBuffer[ActorRef] = ListBuffer()
    var groupList: ListBuffer[ListBuffer[ActorRef]] = ListBuffer()
    for (_ <- 0 until numberOfGroups) {
      groupList += ListBuffer()
    }
    for (i <- 0 until numberOfNodes) {
      val group = Math.floorMod(Utils.hash(InetAddress.getLocalHost.getHostAddress + "joinIp" + i), numberOfGroups)
      nodeList += system.actorOf(Props(new Node("joinIp"+i, writer, numberOfNodes, verbose)), name = "node"+i)
      println("Node " + i + " added to group " + group)
      groupList(group) += nodeList(i)
    }
    // Populate every affinityGroupView with group entries
    for (i <- 0 until numberOfGroups) {
      for (j <- groupList(i).indices) {
        for (k <- j+1 until j+numberOfKnownGroupEntries) {
          if (j != k) {
            groupList(i)(j) ! UtilMessageCases.AddGroupEntry(new GroupEntry(groupList(i)(k%groupList(i).size), 0, 1, -1))
            println(parseActor(groupList(i)(j).toString()) + " added " + parseActor(groupList(i)(k%groupList(i).size).toString()) + " to affinityGroupView")
          }
        }
      }
    }
    Thread.sleep(100)
    // Populate exactly one node with a contact node in exactly one other group
    // Let gossiping do the rest
    for (i <- 0 until numberOfGroups) {
      val groupId = (i+1)%numberOfGroups
      groupList(i).head ! UtilMessageCases.AddContactNode(
        new ContactNode(groupList(groupId).head, groupId, 0, -1)
      )
      println(parseActor(groupList(i).head.toString()) + " added " + parseActor(groupList((i+1)%numberOfGroups).head.toString()) + " to contacts")
    }

    for (i <- 0 until numberOfGroups) {
      println("Group " + i + " has " + groupList(i).size)
    }
  }

  /**
    Start the system with the desired number of nodes and initial known knowledge.
    **/
  def main(args: Array[String]): Unit = {
    initSystem(9, 3, verbose = true)
  }
}