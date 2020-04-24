package testing

import java.net.InetAddress
import java.util.concurrent.{Executors, TimeUnit}

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akkaMessageCases.{FileMessageCases, UtilMessageCases}
import kelips.Main.Node
import kelips.{ContactNode, GroupEntry, Utils}
import kelips.Utils.parseActor

import scala.collection.mutable.ListBuffer
import scala.util.Random

object TestFaultTolerance {
  def faultToleranceTest(numberOfNodes: Int, numberOfKnownGroupEntries: Int = 2, verbose: Boolean): Unit = {
    assert(numberOfKnownGroupEntries > 1)
    val system = ActorSystem("FaultTolerance")
    val writer = system.actorOf(Props(new Writer("testResults", "normal.txt", "")))
    val numberOfGroups: Int = Math.sqrt(numberOfNodes).ceil.toInt
    var nodeList: ListBuffer[ActorRef] = ListBuffer()
    var groupList: ListBuffer[ListBuffer[ActorRef]] = ListBuffer()
    for (_ <- 0 until numberOfGroups) {
      groupList += ListBuffer()
    }
    for (i <- 0 until numberOfNodes) {
      val group = Math.floorMod(Utils.hash(InetAddress.getLocalHost.getHostAddress + "joinIp" + i), numberOfGroups)
      nodeList += system.actorOf(Props(new Node("joinIp" + i, writer, numberOfNodes, verbose)), name = "node" + i)
      println("Node " + i + " added to group " + group)
      groupList(group) += nodeList(i)
    }
    // Populate every affinityGroupView with group entries
    for (i <- 0 until numberOfGroups) {
      for (j <- groupList(i).indices) {
        for (k <- j + 1 until j + numberOfKnownGroupEntries) {
          if (j != k) {
            groupList(i)(j) ! UtilMessageCases.AddGroupEntry(new GroupEntry(groupList(i)(k % groupList(i).size), 0, 1, -1))
            println(parseActor(groupList(i)(j).toString()) + " added " + parseActor(groupList(i)(k % groupList(i).size).toString()) + " to affinityGroupView")
          }
        }
      }
    }
    Thread.sleep(100)
    // Populate exactly one node with a contact node in exactly one other group
    // Let gossiping do the rest
    for (i <- 0 until numberOfGroups) {
      val groupId = (i+1) % numberOfGroups
      groupList(i).head ! UtilMessageCases.AddContactNode(
        new ContactNode(groupList(groupId).head, groupId, 0, -1)
      )
      println(parseActor(groupList(i).head.toString()) + " added " + parseActor(groupList((i + 1) % numberOfGroups).head.toString()) + " to contacts")
    }

    for (i <- 0 until numberOfGroups) {
      println("Group " + i + " has " + groupList(i).size)
    }

    /*********** FILE HANDLING TESTING ************/

    Thread.sleep(5000)

    nodeList.head ! FileMessageCases.InsertFiletupleCall("File0", 3)

    Thread.sleep(3000)

    val nodesToRemove = 1
    var round = 1
    val retrieveRunnable = new Runnable() {
      override def run(): Unit = {
        val percentage = round * 1.toFloat / numberOfNodes * 100
        println("********** Started round " + round + " (" + percentage + "%) *********")
        round += 1

        val r = Random

        for (_ <- 0 until nodesToRemove) {
          val toRemove = r.nextInt(nodeList.size)
          nodeList(toRemove) ! PoisonPill
          nodeList.remove(toRemove)
        }

        Thread.sleep(500)

        for (i <- nodeList.indices) {
          nodeList(i) ! FileMessageCases.LookupFiletupleCall("File0")
        }
        nodeList(r.nextInt(nodeList.size))

      }
    }

    val executor = Executors.newScheduledThreadPool(1)
    executor.scheduleAtFixedRate(retrieveRunnable, 0, 2, TimeUnit.SECONDS)
  }




  /**
    * Getting good test results heavily relies on tweaking the parameters inside 'Node'
    * Setting proper 'crossGroupGossipContactFraction' and 'rttTargetChoosingRate' values when running large systems is necessary
    * @param args
    */
  def main(args: Array[String]): Unit = {
    faultToleranceTest(100, 9, verbose = false)
  }
}
