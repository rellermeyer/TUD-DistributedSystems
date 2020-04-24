package testing

import java.net.InetAddress

import akka.actor.{ActorRef, ActorSystem, Props}
import akkaMessageCases.{FileMessageCases, UtilMessageCases}
import kelips.Main.Node
import kelips.{ContactNode, GroupEntry, Utils}
import kelips.Utils.parseActor
import testing.TestMessageCases.Close

import scala.collection.mutable.ListBuffer

object TestLatency {

  def latencyTest(numberOfNodes: Int, numberOfFiles: Int, numberOfKnownGroupEntries: Int = 2, verbose: Boolean): Unit = {
    assert(numberOfKnownGroupEntries > 1)
    val system = ActorSystem("KelipsLatencyTest")
    val writer = system.actorOf(Props(new Writer("testResults", s"lookupLatencyn=${numberOfNodes}f=$numberOfFiles.txt", "lookup")))
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

    /*********** FILE HANDLING TESTING ************/

    Thread.sleep(10000)
    for (i <- 0 until numberOfFiles) {
      println("trying to insert")
      nodeList(i%numberOfNodes) ! FileMessageCases.InsertFiletupleCall("File" + i)
      Thread.sleep(5)
    }

    Thread.sleep(4000)

    for (i <- 0 until numberOfFiles) {
      nodeList(i%numberOfNodes) ! FileMessageCases.LookupFiletupleCall("File" + i)
      Thread.sleep(10)
    }

    writer ! Close()

    Thread.sleep(1000)

    system.terminate()

    Thread.sleep(1000)
  }

  /**
    * Getting good test results heavily relies on tweaking the parameters inside 'Node'
    * Setting proper 'crossGroupGossipContactFraction' and 'rttTargetChoosingRate' values when running large systems is necessary
    * @param args
    */
  def main(args: Array[String]): Unit = {
    latencyTest(100, 1000, verbose = false)
  }
}
