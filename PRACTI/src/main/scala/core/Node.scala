package core

import java.io.File
import java.nio.file.{Files, Paths}
import java.util.logging.{Level, Logger}

import clock.Clock
import helper.CheckpointSeeder
import invalidationlog.{Checkpoint, CheckpointItem}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object Node {

}

class Node(port: Int, val root: String, hostname: String = "localhost", id: Int) extends VirtualNode(id, hostname, port) {
  val clock = new Clock()
  // Root directory for storing data of @Body
  val dataDir = root + "data/"

  // Root directory for storing log
  val logDir = root + "log/"

  // Root directory for storing checkpoint
  val checkpointDir = root + "checkpoint/"

  val controller = Controller(this)
  val core = new Core(this)
  var neighbours: ListBuffer[VirtualNode] = ListBuffer()
  val checkpoint = new Checkpoint(checkpointDir)

  // Initialize required thingies
  {
    new Thread(core).start()
    // It should be run only once, Checkpoint persists it's state after shutting down the application and initializes from file on start.
    if (glob.seedCheckpoint) {
      logMessage("Checkpoint seeded.")
      val seeder = new CheckpointSeeder(this)
      seeder.seedCheckpoint()
    }
  }

  def addNeighbours(neighbours: List[VirtualNode]): Unit = {
    this.neighbours ++= neighbours

    for (n <- neighbours)
      controller.connectToNodeController(n)
  }

  def addNeighbour(neighbour: VirtualNode): Unit = {
    this.neighbours += neighbour
    controller.connectToNodeController(neighbour)
  }

  //  def invalidate(objectId: String, newStamp : Boolean= true): Unit = {
  //    controller.invalidate(objectId)
  //  }

  def sendToAllNeighbours(body: Body): Unit = {
    for (n <- neighbours) {
      sendBody(n, body)
    }
  }

  def sendBody(virtualNode: VirtualNode, body: Body): Unit = {
    core.sendBody(virtualNode, body)
  }

  def hasBody(filePath: String): Boolean = {
    val path = dataDir + filePath
    Files.exists(Paths.get(path))
  }

  def createBody(filePath: String): Body = Body(dataDir, filePath)

  def hasValidBody(filePath: String): Boolean = {
    val a = this.checkpoint.getById(filePath)
    a match {
      case Some(checkpointItem) => {
        return !checkpointItem.invalid
      }
      case _ => return false
    }
    false
  }

  def getVirtualNode(): VirtualNode = {
    new VirtualNode(id, hostname, port)
  }

}
